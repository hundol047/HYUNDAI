import unittest
import importlib.util
from pathlib import Path
spec = importlib.util.spec_from_file_location("accident", Path(__file__).parents[1] / "fetch_accident_zones.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
convert = module.convert


class AccidentZonesTest(unittest.TestCase):
    def fixture(self):
        # Synthetic schema fixture only, never distributed as road data.
        return dict(afos_fid=123, la_crd='37.5', lo_crd='127.0',
                    geom_json='{"type":"Polygon","coordinates":[[[126.999,37.499],[127.001,37.499],[127.001,37.501],[126.999,37.499]]]}')

    def test_retains_historical_year_and_short_cache_expiry(self):
        row = convert(self.fixture(), 2023, 1000)
        self.assertEqual(row['year'], 2023)
        self.assertEqual(row['validUntilMs'], 1000+90*86400000)
        self.assertGreater(row['radiusMeters'], 100)

    def test_rejects_nonfinite_coordinates_and_missing_polygon(self):
        for changes in [dict(la_crd='nan'), dict(geom_json='{"type":"Point","coordinates":[127,37]}')]:
            with self.assertRaises(ValueError):
                convert(dict(self.fixture(), **changes), 2023, 1000)


if __name__ == '__main__':
    unittest.main()
