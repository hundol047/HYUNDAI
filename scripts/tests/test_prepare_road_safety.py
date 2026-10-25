import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("prepare", Path(__file__).parents[1] / "prepare_road_safety.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

class ImportTest(unittest.TestCase):
    def row(self, **changes):
        return {"위도": "36.7", "경도": "126.5", "도로명": "검사도로", "설치장소": "검사지점", **changes}

    def test_conflicting_roads_are_excluded_not_arbitrarily_chosen(self):
        records, audit = prepare.convert([self.row(), self.row(도로명="다른도로")], "bump", "2025-11-13")
        self.assertEqual([], records)
        self.assertEqual(2, audit["conflictingCoordinateRows"])

    def test_identical_rows_collapse_without_contact_information(self):
        rows, audit = prepare.convert([self.row(관리기관전화번호="DO_NOT_EXPORT")] * 2, "bump", "2025-11-13")
        self.assertEqual(1, len(rows))
        self.assertEqual(1, audit["duplicateRows"])
        self.assertNotIn("DO_NOT_EXPORT", str(rows))

    def test_tm_nan_swapped_and_future_records_are_rejected(self):
        rows = [self.row(위도="200000"), self.row(위도="NaN"), self.row(위도="126.5", 경도="36.7"), self.row(데이터기준일자="2027-01-01")]
        records, audit = prepare.convert(rows, "bump", "2026-10-25")
        self.assertEqual([], records)
        self.assertEqual(4, audit["invalidRows"])

    def test_unknown_speed_never_becomes_zero_speed_warning(self):
        records, _ = prepare.convert([self.row(제한속도="0")], "camera", "2026-10-25")
        self.assertIsNone(records[0][-1])
        records, _ = prepare.convert([self.row(제한속도="30")], "camera", "2026-10-25")
        self.assertEqual(30, records[0][-1])
