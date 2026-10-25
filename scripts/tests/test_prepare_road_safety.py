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
        self.assertIsNone(records[0][6])
        records, _ = prepare.convert([self.row(제한속도="30")], "camera", "2026-10-25")
        self.assertEqual(30, records[0][6])

    def test_national_wgs84_columns_are_used_without_tm_conversion(self):
        row={"WGS84위도":"37.55787859", "WGS84경도":"127.009382", "데이터기준일자":"2021-08-11", "데이터갱신구분":"I"}
        records, audit=prepare.convert([row], "bump", "2026-10-25")
        self.assertEqual(1,audit["accepted"])
        self.assertEqual([37.55787859,127.009382],records[0][1:3])
        self.assertEqual("2021-08-11",records[0][5])

    def test_missing_date_deleted_and_conflicting_columns_are_rejected(self):
        rows=[self.row(데이터기준일자=""),self.row(데이터갱신구분="D"),self.row(WGS84위도="37.0", WGS84경도="127.0")]
        records,audit=prepare.convert(rows,"bump","2026-10-25")
        self.assertEqual([],records)
        self.assertEqual(3,audit["invalidRows"])

    def test_zip_is_read_without_extracting_member_paths(self):
        import tempfile, zipfile
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"source.zip"
            with zipfile.ZipFile(path,"w") as archive:
                archive.writestr("../../outside.csv","WGS84위도,WGS84경도\n37.5,127.0\n")
            rows=prepare.read_rows(path)
            self.assertEqual("37.5",rows[0]["WGS84위도"])
            self.assertEqual([path],list(Path(directory).iterdir()))

    def test_zip_with_multiple_data_files_requires_explicit_selection(self):
        import tempfile, zipfile
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"source.zip"
            with zipfile.ZipFile(path,"w") as archive:
                archive.writestr("one.csv","위도,경도")
                archive.writestr("two.csv","위도,경도")
            with self.assertRaises(ValueError):prepare.read_rows(path)

    def test_newer_date_only_supersedes_an_identical_facility(self):
        rows=[self.row(데이터기준일자="2021-01-01"),self.row(데이터기준일자="2026-05-01")]
        records,audit=prepare.convert(rows,"bump","2026-10-25")
        self.assertEqual("2026-05-01",records[0][5])
        self.assertEqual(1,audit["duplicateRows"])
        rows[1]["설치장소"]="다른시설"
        records,audit=prepare.convert(rows,"bump","2026-10-25")
        self.assertEqual([],records)
        self.assertEqual(2,audit["conflictingCoordinateRows"])

    def test_conflicting_administrative_areas_are_not_merged(self):
        records,audit=prepare.convert([self.row(시군구명="지역A"),self.row(시군구명="지역B")],"bump","2026-10-25")
        self.assertEqual([],records)
        self.assertEqual(2,audit["conflictingCoordinateRows"])

    def test_section_labels_need_explicit_unambiguous_source_information(self):
        hint=prepare.camera_advisory
        self.assertEqual("fixed",hint({},30))
        self.assertEqual("none",hint({"설치장소":"구간 시점"},None))
        self.assertEqual("section_start",hint({"설치장소":"구간단속 시점","단속구간위치구분":"01"},60))
        self.assertEqual("section_end",hint({"설치장소":"구간 종점","단속구간위치구분":"02"},60))
        self.assertEqual("none",hint({"설치장소":"구간 시점","단속구간위치구분":"2"},60))
        self.assertEqual("none",hint({"설치장소":"구간 시점에서 종점"},60))
        self.assertEqual("section",hint({"단속구간위치구분":"1","과속단속구간길이":"2.5"},60))
