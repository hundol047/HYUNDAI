"""Isolated recorder failure-path test; fake ADB, not device execution."""
import unittest
import contextlib, importlib.util, io, json, pathlib, subprocess, sys, tempfile
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('recorder','scripts/record-android-demo.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class AndroidRecorderTest(unittest.TestCase):
    def test_host_sample_records_emulator_memory_without_process_arguments(self):
        with tempfile.TemporaryDirectory() as root:
            proc=pathlib.Path(root);(proc/'meminfo').write_text('MemAvailable: 900000 kB\n')
            for pid,name in [('12','qemu-system-x86'),('13','unrelated')]:
                entry=proc/pid;entry.mkdir();(entry/'comm').write_text(name)
                (entry/'status').write_text('VmRSS: 123456 kB\nThreads: 18\n')
                (entry/'environ').write_text('must not be read')
            sample=m.host_sample(proc)
            self.assertEqual(900000,sample['availableKiB'])
            self.assertEqual([{'pid':12,'rssKiB':123456,'threads':18}],sample['emulators'])
            self.assertNotIn('must not be read',json.dumps(sample))

    def test_disconnect_retains_stdout_startup_and_failed_evidence(self):
        with tempfile.TemporaryDirectory() as root:
            apk=pathlib.Path(root)/'fixture.apk';apk.write_bytes(b'test fixture, not an APK')
            output=pathlib.Path(root)/'evidence'
            def run(cmd,**kwargs):
                if 'uiautomator' in cmd:raise subprocess.CalledProcessError(255,cmd,output=b'ERROR: could not get idle state',stderr=b'error: closed')
                if '-d' in cmd or 'getprop' in cmd or 'exec-out' in cmd:raise subprocess.TimeoutExpired(cmd,kwargs.get('timeout',45))
                raw=b''
                if 'pm' in cmd and 'path' in cmd:raw=b'package:/test/fixture.apk'
                if 'pull' in cmd:pathlib.Path(cmd[-1]).write_bytes(apk.read_bytes())
                return subprocess.CompletedProcess(cmd,0,raw,b'')
            class Process:
                def __init__(self,*args,**kwargs):kwargs['stdout'].write(b'test-only startup marker\n');kwargs['stdout'].flush()
                def terminate(self):pass
                def kill(self):pass
                def wait(self,**kwargs):return 0
            captured=io.StringIO()
            with patch.object(sys,'argv',['recorder','--serial','emulator-5554','--apk',str(apk),'--output',str(output)]),patch.object(m.subprocess,'run',run),patch.object(m.subprocess,'Popen',Process),contextlib.redirect_stdout(captured):
                try:m.main();raise AssertionError('Disconnected device was accepted')
                except subprocess.CalledProcessError as e:assert e.returncode==255
            evidence=json.loads((output/'evidence.json').read_text())
            assert evidence['checks'][-1]['result']=='failed'
            assert evidence['fingerprint']=='unavailable: device connection lost'
            assert 'test-only startup marker' in captured.getvalue()
            assert evidence['pleosValidated'] is False
            assert 'ERROR: could not get idle state' in captured.getvalue()
