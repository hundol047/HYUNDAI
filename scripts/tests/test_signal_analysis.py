import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('analysis',Path(__file__).resolve().parents[1]/'demo/analyze-signal-log.py')
analysis=importlib.util.module_from_spec(spec);spec.loader.exec_module(analysis)

class SignalAnalysisTest(unittest.TestCase):
    def line(self,age,poll,callback,gear='PARK'):
        return f'CabinVehicle: speedAgeMs={age} gear={gear} speedKph=0.0 lastSpeedCallbackElapsedMs={callback} lastPollSuccessElapsedMs={poll} gearAgeMs=8000 gearReadAgeMs=100'
    def test_frozen_timestamp_requires_both_poll_and_callback_progress(self):
        result=analysis.analyze('\n'.join([self.line(4000,5000,5000),self.line(5000,6000,6000)]))
        self.assertEqual('VHAL speed timestamp not refreshed while parked',result['diagnosis'])
        self.assertEqual(5000,result['maxSpeedAgeMs'])
        self.assertTrue(result['pollContinued']);self.assertTrue(result['callbackContinued'])
    def test_stopped_callbacks_and_missing_data_do_not_prove_frozen_vhal(self):
        result=analysis.analyze('\n'.join([self.line(4000,5000,5000),self.line(5000,6000,5000)]))
        self.assertEqual('callbacks/polls stopped',result['diagnosis'])
        self.assertFalse(analysis.analyze('')['sufficientEvidence'])
    def test_driving_is_excluded_and_fresh_park_is_ok(self):
        result=analysis.analyze(self.line(5000,100,100,'DRIVE')+'\n'+self.line(100,100,100))
        self.assertEqual('ok',result['diagnosis']);self.assertEqual(100,result['maxSpeedAgeMs'])
    def test_gear_reread_expiry_does_not_prove_safe_fallback(self):
        text='\n'.join([self.line(4000,5000,5000),self.line(5000,6000,6000)]).replace('gearReadAgeMs=100','gearReadAgeMs=4000')
        self.assertFalse(analysis.analyze(text)['fallbackEvidence'])

    def test_separate_parking_periods_cannot_prove_continuous_receipts(self):
        text='\n'.join([self.line(4000,5000,5000),self.line(100,5500,5500,'DRIVE'),self.line(5000,6000,6000)])
        self.assertFalse(analysis.analyze(text)['fallbackEvidence'])
