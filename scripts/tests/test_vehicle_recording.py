import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / "record-vehicle-emulator.py"
spec = importlib.util.spec_from_file_location("vehicle_recording", path)
recording = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recording)


class RecordingEvidenceTest(unittest.TestCase):
    def event(self, **overrides):
        return dict(event="vehicle_read_state", source="VEHICLE", atMs=10000,
                    connected=True, speedKph=0, gear="PARK", parkedAllowed=True,
                    pleosSdkIncluded=True, brakePressed=False, sdkReadAtMs=10000) | overrides

    def test_empty_or_initialization_only_cannot_pass_sdk_read(self):
        self.assertFalse(recording.evaluate([])["officialSdkReadObserved"])
        self.assertFalse(recording.evaluate([self.event(brakePressed=None, sdkReadAtMs=None)])["officialSdkReadObserved"])

    def test_old_sdk_callback_and_demo_do_not_count_as_real_reads(self):
        result = recording.evaluate([self.event(sdkReadAtMs=1), self.event(source="SIMULATION")])
        self.assertFalse(result["officialSdkReadObserved"])
        self.assertFalse(result["noSimulationObserved"])

    def test_unsafe_driving_unlock_is_not_hidden_by_good_samples(self):
        result = recording.evaluate([self.event(), self.event(gear="DRIVE", speedKph=50)])
        self.assertFalse(result["noUnsafeParkingObserved"])

    def test_a_complete_fixture_passes_only_log_checks_not_emulator_identity(self):
        events = [self.event(), self.event(brakePressed=True),
                  self.event(gear="DRIVE", parkedAllowed=False),
                  self.event(gear="DRIVE", speedKph=35, parkedAllowed=False),
                  self.event(connected=False, gear="UNKNOWN", parkedAllowed=False)]
        self.assertTrue(all(recording.evaluate(events).values()))


if __name__ == "__main__":
    unittest.main()
