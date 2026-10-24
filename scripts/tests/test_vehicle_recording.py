import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / "record-vehicle-emulator.py"
spec = importlib.util.spec_from_file_location("vehicle_recording", path)
recording = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recording)


class RecordingEvidenceTest(unittest.TestCase):
    def event(self, at=10000, **overrides):
        return dict(event="vehicle_read_state", source="VEHICLE", atMs=at,
                    connected=True, speedKph=0, gear="PARK", parkedAllowed=False,
                    pleosSdkIncluded=True, sdkBatteryPercent=68, sdkBatteryReadAtMs=at,
                    sdkChargeSwitch=True, sdkChargeReadAtMs=at, sdkSocUsed=True) | overrides

    def test_empty_or_initialization_only_cannot_pass_sdk_read(self):
        self.assertFalse(recording.evaluate([])["officialSdkReadObserved"])
        self.assertFalse(recording.evaluate([self.event(sdkBatteryPercent=None)])["officialSdkReadObserved"])

    def test_old_sdk_callback_and_demo_do_not_count_as_real_reads(self):
        result = recording.evaluate([self.event(sdkBatteryReadAtMs=1), self.event(source="SIMULATION")])
        self.assertFalse(result["officialSdkReadObserved"])
        self.assertFalse(result["noSimulationObserved"])

    def test_unsafe_driving_unlock_is_not_hidden_by_good_samples(self):
        result = recording.evaluate([self.event(), self.event(gear="DRIVE", speedKph=50, parkedAllowed=True)])
        self.assertFalse(result["noUnsafeParkingObserved"])

    def test_missing_debounce_or_display_only_soc_cannot_pass(self):
        result = recording.evaluate([self.event(parkedAllowed=True), self.event(12000,sdkBatteryPercent=80)])
        self.assertFalse(result["parkingDebounceObserved"])
        self.assertFalse(result["sdkSocUsedInPlan"])

    def test_complete_fixture_passes_log_checks_only_not_emulator_identity(self):
        events = [self.event(), self.event(11600,parkedAllowed=True),
                  self.event(13000,gear="DRIVE"), self.event(14000,gear="DRIVE",speedKph=35),
                  self.event(15000), self.event(16600,parkedAllowed=True,sdkBatteryPercent=80,
                      planSource="Pleos SOC 목표 도달",availableSeconds=0,sdkChargeSwitch=False),
                  self.event(17000,connected=False,gear="UNKNOWN")]
        self.assertTrue(all(recording.evaluate(events).values()))


if __name__ == "__main__":
    unittest.main()
