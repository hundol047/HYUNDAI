#!/usr/bin/env python3
"""Opt-in, authenticated short-lived road observations. No report is proof of an accident."""
import hashlib
import hmac
import json
import math
import os
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from accident_zones import load_zones, metadata


def number(value, lo, hi):
    if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or not lo <= value <= hi:
        raise ValueError('Invalid numeric field')
    return value


def distance(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, [a['lat'], a['lon'], b['lat'], b['lon']])
    v = math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 12742000*math.asin(math.sqrt(min(1, max(0, v))))


def heading_gap(a, b):
    return abs((a-b+180) % 360-180)


def observation(body, now):
    if set(body) != {'id', 'lat', 'lon', 'heading', 'observedAtMs', 'deceleration'}:
        raise ValueError('Unexpected report fields')
    ident = body['id']
    if not isinstance(ident, str) or not 8 <= len(ident) <= 80 or not all(c in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-' for c in ident):
        raise ValueError('Invalid report id')
    number(body['lat'], -85, 85); number(body['lon'], -180, 180)
    number(body['heading'], 0, 359.999); number(body['deceleration'], 3, 15)
    number(body['observedAtMs'], now-30000, now+2000)
    return dict(body, lat=round(body['lat'], 5), lon=round(body['lon'], 5))


class Store:
    def __init__(self, clock=lambda: int(time.time()*1000)):
        self.clock = clock; self.reports = {}; self.last = {}; self.lock = threading.Lock()

    def prune(self):
        now = self.clock()
        self.reports = {k:v for k,v in self.reports.items() if now-v['observedAtMs'] < 900000}
        self.last = {k:v for k,v in self.last.items() if now-v < 900000}

    def add(self, reporter, body):
        with self.lock:
            self.prune(); now = self.clock(); report = observation(body, now); key = (reporter, report['id'])
            if key in self.reports: return False
            if now-self.last.get(reporter, -10**15) < 30000 or len(self.reports) >= 10000:
                raise OverflowError('Report limit')
            self.last[reporter] = now; self.reports[key] = report
            return True

    def nearby(self, point):
        with self.lock:
            self.prune(); groups = []
            for (reporter, _), report in sorted(self.reports.items(), key=lambda kv: kv[1]['observedAtMs'], reverse=True):
                if distance(report, point) > 2000: continue
                group = next((g for g in groups if distance(g['first'], report) <= 80 and heading_gap(g['first']['heading'], report['heading']) <= 30), None)
                if group is None:
                    group = {'first': report, 'people': set(), 'times': []}; groups.append(group)
                group['people'].add(reporter); group['times'].append(report['observedAtMs'])
            result = []
            for g in groups:
                # Multiple reports from one token/installation never become corroboration.
                count = len(g['people'])
                if count < 2: continue
                r = g['first']; fingerprint = f"{round(r['lat'],3)}:{round(r['lon'],3)}:{int(r['heading']//30)}:{min(g['times'])//900000}"
                result.append({'id':hashlib.sha256(fingerprint.encode()).hexdigest()[:20], 'lat':r['lat'], 'lon':r['lon'],
                    'heading':r['heading'], 'kind':'repeated_braking', 'reporters':count,
                    'observedAtMs':max(g['times']), 'expiresAtMs':min(g['times'])+900000,
                    'source':'authenticated community observations; not verified accident data'})
            return sorted(result, key=lambda r:distance(r, point))[:50]


class EchoServer(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self, address, tokens, zones=None, store=None):
        if not tokens or len(tokens)>5000 or any(not isinstance(k,str) or len(k)<24 or not k.isascii() or not isinstance(v,str) or not 1<=len(v)<=80 for k,v in tokens.items()):
            raise ValueError('Configure per-installation tokens and opaque identities')
        self.tokens=tokens;self.store=store or Store();self.zones=zones or []
        self.dataset_sha=metadata(self.zones,self.store.clock())["sha256"]
        self.public_window=0.0;self.public_reads=0
        self.get_last={};self.rate_lock=threading.Lock();self.slots=threading.BoundedSemaphore(16)
        super().__init__(address, Handler)

    def nearby_zones(self, point):
        now=self.store.clock()
        candidates=sorted((r for r in self.zones if r['validUntilMs']>now and distance(r,point)<=2000),key=lambda r:distance(r,point))
        # Leave room for community reports below the Android client's 200KB response cap.
        result=[];size=0
        for r in candidates[:50]:
            encoded=len(json.dumps(r,separators=(',',':')).encode())
            if size+encoded>150000:break
            result.append(r);size+=encoded
        return result,len(candidates)>len(result)

    def service_actions(self):
        with self.store.lock:self.store.prune()
    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request);return
        try:super().process_request(request,address)
        except BaseException:self.slots.release();raise
    def process_request_thread(self, request, address):
        try:super().process_request_thread(request,address)
        finally:self.slots.release()


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup();self.connection.settimeout(10)
    def log_message(self,*args):pass
    def reply(self,code,value):
        b=json.dumps(value,separators=(',',':')).encode();self.send_response(code)
        self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
        self.send_header('Content-Length',str(len(b)));self.end_headers();self.wfile.write(b)
    def identity(self):
        auth=self.headers.get('Authorization','')
        if len(auth)>300:return None
        return next((v for k,v in self.server.tokens.items() if hmac.compare_digest(auth.encode(),('Bearer '+k).encode())),None)
    def do_POST(self):
        who=self.identity()
        if who is None:return self.reply(401,{'error':'Unauthorized'})
        if self.path!='/v1/observations':return self.reply(404,{'error':'Not found'})
        try:
            size=int(self.headers.get('Content-Length','0'))
            if not 1<=size<=2048 or self.headers.get('Transfer-Encoding'):raise ValueError()
            body=json.loads(self.rfile.read(size));created=self.server.store.add(who,body)
            self.reply(201 if created else 200,{'accepted':True,'published':False,'notice':'Requires independent corroboration'})
        except OverflowError:self.reply(429,{'error':'Report rate or capacity limit'})
        except (ValueError,TypeError,KeyError):self.reply(400,{'error':'Invalid observation'})
    def query_point(self,u):
        q=urllib.parse.parse_qs(u.query);p={'lat':float(q['lat'][0]),'lon':float(q['lon'][0])}
        number(p['lat'],-85,85);number(p['lon'],-180,180)
        return p
    def do_GET(self):
        if self.path=='/healthz':return self.reply(200,{'status':'ok'})
        u=urllib.parse.urlsplit(self.path)
        if u.path in ('/v1/dataset','/v1/accident-zones'):
            with self.server.rate_lock:
                now=time.monotonic()
                if now-self.server.public_window>=1:self.server.public_window=now;self.server.public_reads=0
                if self.server.public_reads>=20:return self.reply(429,{'error':'Public read limit'})
                self.server.public_reads+=1
        if u.path=='/v1/dataset':return self.reply(200,metadata(self.server.zones,self.server.store.clock(),self.server.dataset_sha))
        # Public official statistics only. Never return community observations on this endpoint.
        if u.path=='/v1/accident-zones':
            try:p=self.query_point(u)
            except (ValueError,KeyError,TypeError):return self.reply(400,{'error':'Invalid coordinates'})
            zones,truncated=self.server.nearby_zones(p)
            return self.reply(200,{'historicalOnly':True,'accidentZones':zones,'truncated':truncated,'radiusMeters':2000})
        who=self.identity()
        if who is None:return self.reply(401,{'error':'Unauthorized'})
        if u.path=='/v1/status':
            return self.reply(200,{'status':'ok','accidentDatasetConnected':any(r['validUntilMs']>self.server.store.clock() for r in self.server.zones)})
        if u.path!='/v1/risks':return self.reply(404,{'error':'Not found'})
        try:
            p=self.query_point(u)
        except (ValueError,KeyError,TypeError):return self.reply(400,{'error':'Invalid coordinates'})
        now=self.server.store.clock()
        with self.server.rate_lock:
            if now-self.server.get_last.get(who,-10**15)<10000:return self.reply(429,{'error':'Read rate limit'})
            self.server.get_last[who]=now
        zones,truncated=self.server.nearby_zones(p)
        self.reply(200,{'generatedAtMs':now,'coverage':'reports only; absence is not evidence of safety',
            'accidentDatasetConnected':any(r['validUntilMs']>now for r in self.server.zones),'risks':self.server.store.nearby(p),
            'accidentZones':zones,'accidentZonesTruncated':truncated})


def bind_address(env):
    host=env.get('CABIN_ECHO_HOST','127.0.0.1')
    port=int(env.get('PORT',env.get('CABIN_ECHO_PORT','8012')))
    if not host or not 1<=port<=65535:raise ValueError('Invalid listen address')
    return host,port


if __name__=='__main__':
    tokens=json.loads(os.environ['CABIN_ECHO_TOKENS_JSON'])
    zones=load_zones(os.environ.get('CABIN_ACCIDENT_ZONES_JSON',os.path.join(os.path.dirname(__file__),'data','accident-zones.json')),int(time.time()*1000))
    EchoServer(bind_address(os.environ),tokens,zones).serve_forever()
