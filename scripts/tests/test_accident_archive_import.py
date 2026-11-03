import importlib.util
from pathlib import Path
import json
import unittest
spec=importlib.util.spec_from_file_location('archive',Path(__file__).parents[1]/'import_accident_archives.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class ArchiveTest(unittest.TestCase):
    def row(self, ident, fid='1'):
        return {'사고다발지id':ident,'사고다발지fid':fid,'위도':'37.5','경도':'127',
                '다발지역폴리곤':json.dumps({'type':'MultiPolygon','coordinates':[[[[127,37.5],[127.001,37.5],[127.001,37.501],[127,37.5]]]]})}
    def test_official_period_is_not_identifier_prefix(self):
        mapping={'2025113':'20-24년 결빙 교통사고 다발지역','2026125':'21-25년 결빙 교통사고 다발지역'}
        rows,audit=m.convert([self.row('2025113'),self.row('2026125','2')],'icy','결빙',mapping,1798416000000)
        self.assertEqual(1,len(rows));self.assertEqual((2021,2025),(rows[0]['periodStart'],rows[0]['year']))
        self.assertEqual(1,audit['olderPeriodRows']);self.assertEqual('MultiPolygon',rows[0]['geometry']['type'])
    def test_unknown_period_fails_instead_of_guessing(self):
        with self.assertRaises(KeyError):m.convert([self.row('2026125')],'icy','결빙',{},1)
    def test_identical_duplicates_collapse_but_conflicts_fail(self):
        mapping={'2026125':'21-25년 결빙 교통사고 다발지역'};r=self.row('2026125')
        rows,audit=m.convert([r,r],'icy','결빙',mapping,1)
        self.assertEqual(1,len(rows));self.assertEqual(1,audit['identicalDuplicateRows'])
        with self.assertRaises(ValueError):m.convert([r,dict(r,위도='37.5001')],'icy','결빙',mapping,1)
