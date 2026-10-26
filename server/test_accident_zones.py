import copy
import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from accident_zones import load_zones, metadata, validate_geometry
from echoroad_gateway import EchoServer

NOW=1793000000000
DATA=Path(__file__).parent/'data/accident-zones.json'

class AccidentZonesTest(unittest.TestCase):
    def test_bundled_latest_periods_match_official_code_table(self):
        zones=load_zones(DATA,NOW);m=metadata(zones,NOW)
        self.assertEqual(1271,len(zones))
        self.assertEqual({'municipal':683,'pedestrian':418,'child_pedestrian':88,'icy':82},{k:v['count'] for k,v in m['categories'].items()})
        self.assertEqual(2021,m['categories']['icy']['periodStart'])
        self.assertEqual(2023,m['categories']['pedestrian']['periodStart'])
        self.assertEqual(json.loads(DATA.with_name('accident-zones-audit.json').read_text())['sha256'],m['sha256'])
    def test_expired_dataset_is_not_served_but_does_not_crash_shared_server(self):
        zones=load_zones(DATA,1798416000001)
        self.assertEqual(0,metadata(zones,1798416000001)['zoneCount'])
    def test_multipolygon_and_holes_are_validated(self):
        ring=[[127,37],[127.001,37],[127.001,37.001],[127,37]]
        validate_geometry({'type':'MultiPolygon','coordinates':[[ring,ring],[ring]]})
        with self.assertRaises(ValueError):validate_geometry({'type':'Polygon','coordinates':[ring[:-1]]})
    def test_public_dataset_query_never_exposes_private_reports(self):
        zones=load_zones(DATA,NOW);r=zones[0]
        server=EchoServer(('127.0.0.1',0),{'a'*24:'test-device'},zones)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            base=f'http://127.0.0.1:{server.server_port}'
            with urllib.request.urlopen(base+f"/v1/accident-zones?lat={r['lat']}&lon={r['lon']}",timeout=3) as response:
                body=json.load(response)
            self.assertTrue(body['historicalOnly']);self.assertIn(r['id'],[x['id'] for x in body['accidentZones']])
            self.assertNotIn('risks',body);self.assertNotIn('test-device',json.dumps(body))
            with self.assertRaises(urllib.error.HTTPError) as error:urllib.request.urlopen(base+'/v1/risks?lat=37.5&lon=127',timeout=3)
            self.assertEqual(401,error.exception.code)
        finally:server.shutdown();server.server_close();thread.join()
