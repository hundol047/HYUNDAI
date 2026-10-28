import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('release_candidate', Path(__file__).parents[1] / 'verify-release-candidate.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseCandidateTest(unittest.TestCase):
    def check(self, version='0.11.1', code=20, debug=False, sdk='0xffffffff', certificate='ab' * 32):
        badging = f"package: name='io.cabinos.one' versionCode='{code}' versionName='{version}'\n"
        if debug:
            badging += 'application-debuggable\n'
        manifest = f'E: meta-data\n  A: android:name="io.cabinos.one.PLEOS_SDK_ENABLED"\n  A: android:value=(type 0x12){sdk}\nE: activity\n'
        signer = f'Signer #1 certificate SHA-256 digest: {certificate}\n'
        return module.inspect_metadata(badging, manifest, signer, '0.11.1', 20, 'ab' * 32)

    def test_accepts_matching_signed_release_metadata(self):
        self.assertEqual([], self.check())

    def test_rejects_old_version_even_when_signed(self):
        self.assertIn('APK 버전이 현재 소스와 다릅니다.', self.check(version='0.11.0', code=19))

    def test_rejects_debuggable_candidate(self):
        self.assertIn('디버그 가능 APK는 배포할 수 없습니다.', self.check(debug=True))

    def test_rejects_missing_or_disabled_sdk_marker(self):
        self.assertIn('Pleos SDK 포함 빌드 표시가 없습니다.', self.check(sdk='0x0'))
        self.assertIn('Pleos SDK 포함 빌드 표시가 없습니다.', module.inspect_metadata('', '', '', '0.11.1', 20, 'ab' * 32))

    def test_rejects_unexpected_signing_certificate(self):
        self.assertIn('서명 인증서가 지정한 팀 인증서와 다릅니다.', self.check(certificate='cd' * 32))

    def test_rejects_debug_certificate_even_for_a_matching_fingerprint(self):
        signer = 'Signer #1 certificate SHA-256 digest: ' + 'ab' * 32 + '\nSigner #1 certificate DN: CN=Android Debug,O=Android,C=US'
        self.assertIn('Android debug 서명은 배포할 수 없습니다.', module.inspect_metadata('', '', signer, '0.11.1', 20, 'ab' * 32))

    def test_rejects_different_application_id(self):
        badging = "package: name='other.application' versionCode='20' versionName='0.11.1'"
        self.assertIn('앱 패키지가 CabinOS ONE과 다릅니다.', module.inspect_metadata(badging, '', '', '0.11.1', 20, 'ab' * 32))

    def test_checksum_requires_exact_filename_and_digest(self):
        self.assertTrue(module.checksum_matches('ab' * 32 + '  apk/team_release.apk\n', 'team_release.apk', 'ab' * 32))
        self.assertFalse(module.checksum_matches('ab' * 32 + '  apk/other.apk\n', 'team_release.apk', 'ab' * 32))
        self.assertFalse(module.checksum_matches('cd' * 32 + '  apk/team_release.apk\n', 'team_release.apk', 'ab' * 32))
