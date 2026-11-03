import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("download", Path(__file__).parents[1] / "download_camera_data.py")
download = importlib.util.module_from_spec(spec)
spec.loader.exec_module(download)

class DownloadTest(unittest.TestCase):
    header = {"totalCount": 3, "columList": [{"columCode":"ID", "columNm":"번호"}]}

    def test_two_parts_map_fields_and_preserve_all_rows(self):
        self.assertEqual([{"번호":1},{"번호":2},{"번호":3}],download.combine(self.header,[{"ID":1},{"ID":2}],[{"ID":3}]))

    def test_short_or_overlapping_parts_are_not_published(self):
        with self.assertRaises(ValueError):download.combine(self.header,[{"ID":1}],[{"ID":3}])
        with self.assertRaises(ValueError):download.combine(self.header,[{"ID":1},{"ID":2}],[{"ID":2}])

    def test_export_limit_is_not_bypassed(self):
        with self.assertRaises(ValueError):download.combine({**self.header,"totalCount":50_001},[],[])
