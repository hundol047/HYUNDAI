import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('scenario', Path(__file__).resolve().parents[1] / 'demo/full_scenario.py')
scenario = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scenario)

class ScenarioEvidenceTest(unittest.TestCase):
    def test_empty_evidence_never_passes(self):
        for step in scenario.STEPS:
            self.assertFalse(scenario.verdict(step, []))

    def test_simulation_or_unsafe_unlock_cannot_pass(self):
        for source in ('SIMULATION', 'VEHICLE'):
            self.assertFalse(scenario.verdict('park', [dict(source=source, connected=True,
                gear='DRIVE', speedKph=40, parkedAllowed=False, atMs=1000)]))

    def test_park_requires_three_seconds_of_continuous_real_park(self):
        e = dict(source='VEHICLE', connected=True, gear='PARK', speedKph=0,
                 parkedAllowed=False, atMs=1000)
        self.assertFalse(scenario.verdict('park', [e]))
        self.assertTrue(scenario.verdict('park', [e, e | dict(atMs=2000), e | dict(atMs=3000,parkedAllowed=True), e | dict(atMs=4000,parkedAllowed=True)]))
        self.assertFalse(scenario.verdict('park', [e, e | dict(atMs=2000, connected=False), e | dict(atMs=4000)]))

    def test_soc_requires_crossing_and_session_end_not_display_only(self):
        e = dict(source='VEHICLE',connected=True,gear='PARK',speedKph=0,parkedAllowed=True,
                 sdkSocUsed=True,chargeTargetPercent=80,sdkBatteryPercent=70,sessionStatus='RUNNING',atMs=1000)
        reached=e | dict(atMs=2000,sdkBatteryPercent=80,sessionStatus='COMPLETED',context='PREPARING_DEPARTURE',planSource='Pleos SOC 목표 도달')
        self.assertFalse(scenario.verdict('soc', [reached]))
        self.assertFalse(scenario.verdict('soc', [e,reached | dict(sessionStatus='RUNNING')]))
        self.assertTrue(scenario.verdict('soc', [e,reached]))

    def test_startup_unknown_is_allowed_but_premature_unlock_is_not(self):
        e=dict(source='VEHICLE',connected=True,gear='PARK',speedKph=0,parkedAllowed=False,atMs=1000)
        rows=[e | dict(atMs=0,connected=False,gear='UNKNOWN'),e,e | dict(atMs=2000),e | dict(atMs=3000,parkedAllowed=True),e | dict(atMs=4000,parkedAllowed=True)]
        self.assertTrue(scenario.verdict('park',rows))
        self.assertFalse(scenario.verdict('park',[r | dict(parkedAllowed=True) for r in rows[1:]]))

    def test_recording_must_cover_steps_and_be_a_successful_continuous_video(self):
        self.assertTrue(scenario.recording_ok(0,120,90,10000))
        self.assertFalse(scenario.recording_ok(1,120,90,10000))
        self.assertFalse(scenario.recording_ok(0,70,90,10000))
        self.assertFalse(scenario.recording_ok(0,120,90,0))

if __name__ == '__main__':
    unittest.main()
