import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('packaging', Path(__file__).parents[1] / 'prepare-submission.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SubmissionPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'repo'
        self.root.mkdir()
        (self.root / 'apk').mkdir()
        self.old = self.root / 'apk' / module.APK_NAME
        self.old.write_bytes(b'existing submitted binary')
        self.unsigned = self.root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
        self.unsigned.parent.mkdir(parents=True)
        self.unsigned.write_bytes(b'fixture unsigned binary; not an APK')
        self.keydir = self.base / 'keys'
        self.keydir.mkdir()
        (self.keydir / 'release.p12').write_text('test fixture; not a key')
        (self.keydir / 'password').write_text('test fixture')
        self.output = self.root / 'artifacts/candidate'
        self.calls = []
        self.fail = None
        self.dirty = False

    def runner(self, command, **kwargs):
        command = list(map(str, command))
        self.calls.append(command)
        if self.fail and any(self.fail in part for part in command):
            raise subprocess.CalledProcessError(1, command)
        if command[:2] == ['git', 'status']:
            return subprocess.CompletedProcess(command, 0, ' M file' if self.dirty else '')
        if command[:2] == ['git', 'rev-parse']:
            return subprocess.CompletedProcess(command, 0, 'a' * 40)
        if 'sign' in command:
            Path(command[command.index('--out') + 1]).write_bytes(b'fixture signed output; not an APK')
        if any(part.endswith('verify-release-candidate.py') for part in command):
            Path(command[command.index('--output') + 1]).write_text(json.dumps({'binaryChecksPassed': True}))
        return subprocess.CompletedProcess(command, 0, '')

    def prepare(self):
        return module.prepare(self.root, self.keydir, 'ab' * 32, self.base / 'sdk', self.output, self.runner)

    def test_verified_candidate_never_replaces_submission(self):
        self.prepare()
        self.assertEqual(b'existing submitted binary', self.old.read_bytes())
        self.assertTrue((self.output / module.APK_NAME).is_file())
        report = json.loads((self.output / 'candidate.json').read_text())
        self.assertEqual('퓨처랩스', report['team'])
        self.assertFalse(report['deviceValidated'])
        self.assertFalse(report['published'])

    def test_failed_verification_discards_candidate_and_preserves_submission(self):
        self.fail = 'verify-release-candidate.py'
        with self.assertRaises(subprocess.CalledProcessError):
            self.prepare()
        self.assertFalse(self.output.exists())
        self.assertEqual(b'existing submitted binary', self.old.read_bytes())
        self.assertEqual([], list(self.output.parent.glob('.candidate-*')))

    def test_failed_build_never_publishes_a_candidate(self):
        self.fail = ':app:assembleRelease'
        with self.assertRaises(subprocess.CalledProcessError):
            self.prepare()
        self.assertFalse(self.output.exists())
        self.assertFalse(any('sign' in call for call in self.calls))

    def test_existing_output_is_not_overwritten(self):
        self.output.mkdir(parents=True)
        with self.assertRaises(FileExistsError):
            self.prepare()
        self.assertEqual([], self.calls)

    def test_keys_and_output_cannot_target_repository_submission(self):
        self.output = self.root / 'apk/candidate'
        with self.assertRaises(ValueError):
            self.prepare()
        self.output = self.root / 'artifacts/candidate'
        self.keydir = self.root / 'keys'
        with self.assertRaises(ValueError):
            self.prepare()

    def test_dirty_source_is_not_attested_as_a_clean_commit(self):
        self.dirty = True
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())
