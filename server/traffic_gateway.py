#!/usr/bin/env python3
"""Authenticated, bounded flow gateway. No Korean requests in this deployment profile."""
import hmac, json, math, os, threading, time, urllib.parse, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def korean(lat, lon):
    # Conservative exclusion, including border areas. A domestic deployment needs separate review.
    return 32 <= lat <= 40 and 124 <= lon <= 132


def point(query):
    p = urllib.parse.parse_qs(query).get('point', [''])[0].split(',')
    if len(p) != 2:
        raise ValueError('Invalid point')
    lat, lon = map(float, p)
    if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180 or korean(lat, lon):
        raise ValueError('Unsupported point')
    return lat, lon


def normalize(data, now):
    f = data['flowSegmentData']
    coords = [[float(p['latitude']), float(p['longitude'])] for p in f['coordinates']['coordinate']]
    if not 2 <= len(coords) <= 2000 or any(not math.isfinite(a) or not math.isfinite(b) or not -90 <= a <= 90 or not -180 <= b <= 180 or korean(a,b) for a,b in coords):
        raise ValueError('Invalid geometry')
    current, free, confidence = float(f['currentSpeed']), float(f['freeFlowSpeed']), float(f['confidence'])
    if not all(math.isfinite(x) for x in [current, free, confidence]) or current < 0 or free <= 0 or not 0 <= confidence <= 1:
        raise ValueError('Invalid flow')
    return dict(points=coords, currentSpeed=current, freeFlowSpeed=free, confidence=confidence,
                closed=f.get('roadClosure') is True, fetchedAtMs=int(now*1000), source='TomTom')


class TrafficServer(ThreadingHTTPServer):
    def __init__(self, address, key, token):
        if not key or len(token) < 24: raise ValueError('Configure provider key and private gateway token')
        super().__init__(address, Handler)
        self.key, self.token = key, token
        self.lock = threading.Lock(); self.cache = {}; self.last = 0.; self.calls = 0
        self.budget = int(os.environ.get('CABIN_TRAFFIC_REQUEST_BUDGET', '100'))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def reply(self, code, value):
        body=json.dumps(value).encode();self.send_response(code)
        self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
        self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def do_GET(self):
        auth=self.headers.get('Authorization','')
        if not hmac.compare_digest(auth.encode(),('Bearer '+self.server.token).encode()):return self.reply(401,{'error':'Unauthorized'})
        u=urllib.parse.urlsplit(self.path)
        if u.path!='/flow':return self.reply(404,{'error':'Not found'})
        try: lat,lon=point(u.query)
        except (ValueError,TypeError):return self.reply(400,{'error':'Unsupported coordinates; Korean traffic disabled'})
        # No queue of paid provider calls, and a finite per-process test budget.
        if not self.server.lock.acquire(blocking=False):return self.reply(429,{'error':'Busy'})
        try:
            now=time.time(); cachekey=(round(lat,4),round(lon,4))
            self.server.cache={k:v for k,v in self.server.cache.items() if now-v[0]<30}
            if cachekey in self.server.cache:return self.reply(200,self.server.cache[cachekey][1])
            if now-self.server.last<2 or self.server.calls>=self.server.budget:return self.reply(429,{'error':'Request budget or rate limit'})
            self.server.last=now;self.server.calls+=1
            url='https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json?'+urllib.parse.urlencode(dict(point=f'{lat},{lon}',unit='KMPH',key=self.server.key))
            with urllib.request.urlopen(url,timeout=12) as response:
                raw=response.read(500001)
                if len(raw)>500000:raise ValueError('Response too large')
                result=normalize(json.loads(raw),time.time())
            self.server.cache[cachekey]=(now,result);self.reply(200,result)
        except Exception:return self.reply(502,{'error':'Traffic unavailable'})
        finally:self.server.lock.release()

if __name__=='__main__':
    TrafficServer(('127.0.0.1',int(os.environ.get('CABIN_TRAFFIC_PORT','8010'))),os.environ.get('TOMTOM_SERVER_API_KEY',''),os.environ.get('CABIN_TRAFFIC_TOKEN','')).serve_forever()
