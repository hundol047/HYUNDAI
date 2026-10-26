import json
import threading
import unittest
import urllib.request
import urllib.error
from echoroad_gateway import Store, EchoServer, bind_address


class EchoRoadTest(unittest.TestCase):
    def setUp(self):
        self.now=1000000
        self.store=Store(lambda:self.now)
    def report(self, ident='event-0001', heading=0):
        return dict(id=ident,lat=37.5,lon=127.0,heading=heading,observedAtMs=self.now,deceleration=4)
    def test_independent_confirmation_expiry_and_opposite_direction(self):
        a=self.report();self.store.add('one',a)
        self.assertEqual([],self.store.nearby(a))
        self.assertFalse(self.store.add('one',a))
        self.store.add('two',self.report('event-0002',180))
        self.assertEqual([],self.store.nearby(a))
        self.store.add('three',self.report('event-0003'))
        self.assertEqual(2,self.store.nearby(a)[0]['reporters'])
        self.now+=900001;self.assertEqual([],self.store.nearby(a))
    def test_single_reporter_and_stale_or_malformed_reports(self):
        self.store.add('one',self.report());self.now+=31000
        self.store.add('one',self.report('event-0002'))
        self.assertEqual([],self.store.nearby(self.report()))
        for overrides in [dict(lat=float('nan')),dict(observedAtMs=0),dict(deceleration=1),dict(lat=True),dict(kind='accident')]:
            with self.assertRaises(ValueError):self.store.add('new',dict(self.report(),**overrides))
    def test_http_auth_and_two_independent_clients(self):
        keys={'a'*24:'one','b'*24:'two'}
        server=EchoServer(('127.0.0.1',0),keys,store=self.store)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        base=f'http://127.0.0.1:{server.server_port}'
        def call(path,key,body=None):
            request=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'})
            with urllib.request.urlopen(request,timeout=3) as response:return response.status,json.load(response)
        try:
            with self.assertRaises(urllib.error.HTTPError) as error:call('/v1/risks?lat=37.5&lon=127','x'*24)
            self.assertEqual(401,error.exception.code)
            self.assertEqual(201,call('/v1/observations','a'*24,self.report())[0])
            self.assertEqual(201,call('/v1/observations','b'*24,self.report('event-0002'))[0])
            status,data=call('/v1/risks?lat=37.5&lon=127','a'*24)
            self.assertEqual(200,status);self.assertEqual(2,data['risks'][0]['reporters'])
            self.assertFalse(data['accidentDatasetConnected'])
            self.assertNotIn('"one"',json.dumps(data));self.assertNotIn('a'*24,json.dumps(data))
        finally:server.shutdown();server.server_close();thread.join()

    def test_rate_limit_and_idle_expiry(self):
        self.store.add('one',self.report())
        with self.assertRaises(OverflowError):
            self.store.add('one',self.report('event-0002'))
        server=EchoServer(('127.0.0.1',0),{'a'*24:'one'},store=self.store)
        try:
            self.now+=900001
            server.service_actions()
            self.assertEqual({},self.store.reports)
            self.assertEqual({},self.store.last)
        finally:
            server.server_close()

    def test_railway_port_and_explicit_public_bind(self):
        self.assertEqual(('127.0.0.1',8012),bind_address({}))
        self.assertEqual(('0.0.0.0',8080),bind_address({'CABIN_ECHO_HOST':'0.0.0.0','PORT':'8080','CABIN_ECHO_PORT':'8012'}))
        for port in ['0','65536','abc']:
            with self.assertRaises(ValueError):bind_address({'PORT':port})

    def test_healthcheck_without_credentials_does_not_expose_reports(self):
        server=EchoServer(('127.0.0.1',0),{'a'*24:'one'},store=self.store)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            base=f'http://127.0.0.1:{server.server_port}'
            with urllib.request.urlopen(base+'/healthz',timeout=3) as response:
                self.assertEqual(200,response.status)
                self.assertEqual({'status':'ok'},json.load(response))
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(base+'/v1/risks?lat=37.5&lon=127',timeout=3)
            self.assertEqual(401,error.exception.code)
        finally:server.shutdown();server.server_close();thread.join()
