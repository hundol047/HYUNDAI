"""Validator unit tests only; these fixtures are not application execution evidence."""
import array
import importlib.util
import math
from pathlib import Path
import sys
import unittest

spec = importlib.util.spec_from_file_location('recorded_audio', Path(__file__).resolve().parents[1] / 'verify_recorded_audio.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CapturedAudioMeasurementTest(unittest.TestCase):
    def pcm(self, values):
        data = array.array('h', values)
        if sys.byteorder != 'little':
            data.byteswap()
        return data.tobytes()

    def test_silence_and_empty_capture_do_not_count_as_playback(self):
        for data in [b'', self.pcm([0] * 16000)]:
            self.assertLess(module.measure(data)['rmsDbfs'], -65)

    def test_nonzero_pcm_has_expected_rms_and_peak(self):
        result = module.measure(self.pcm([1000, -1000] * 8000))
        self.assertAlmostEqual(20 * math.log10(1000 / 32768), result['rmsDbfs'])
        self.assertEqual(1000, result['peak'])
        self.assertEqual(16000, result['samples'])

    def test_different_capture_has_different_digest(self):
        self.assertNotEqual(module.measure(self.pcm([1000] * 100))['pcmSha256'],
                            module.measure(self.pcm([2000] * 100))['pcmSha256'])
