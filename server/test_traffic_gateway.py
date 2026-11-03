import unittest
from traffic_gateway import point, normalize

class TrafficTests(unittest.TestCase):
    def test_korea_and_invalid_coordinates_rejected(self):
        for q in ['point=37.5,127','point=nan,4','point=91,4','point=1,inf','point=1']:
            with self.assertRaises(ValueError): point(q)
        self.assertEqual((52.376,4.9),point('point=52.376,4.9'))
    def fixture(self):
        return {'flowSegmentData':{'coordinates':{'coordinate':[{'latitude':52.3,'longitude':4.9},{'latitude':52.31,'longitude':4.91}]},'currentSpeed':0,'freeFlowSpeed':50,'confidence':.9,'roadClosure':True}}
    def test_real_fields_preserved_without_provider_key(self):
        f=normalize(self.fixture(),100)
        self.assertEqual(0,f['currentSpeed']);self.assertTrue(f['closed']);self.assertEqual(100000,f['fetchedAtMs'])
        self.assertNotIn('key',f)
    def test_bad_geometry_or_speed_not_presented_as_free_flow(self):
        f=self.fixture();f['flowSegmentData']['freeFlowSpeed']=0
        with self.assertRaises(ValueError):normalize(f,100)
        f=self.fixture();f['flowSegmentData']['coordinates']['coordinate'][0]={'latitude':37.5,'longitude':127}
        with self.assertRaises(ValueError):normalize(f,100)
