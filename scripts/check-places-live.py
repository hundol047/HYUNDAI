#!/usr/bin/env python3
"""Small explicit business/route coverage sample, never a worldwide enumeration.

--reuse-search-capture reuses real responses captured by the initial manual probe;
it is explicitly recorded as a replay, not presented as a fresh HTTP request.
"""
import argparse, datetime, hashlib, json, math, time, urllib.parse, urllib.request, urllib.error
from pathlib import Path

parser=argparse.ArgumentParser();parser.add_argument('--reuse-search-capture',action='store_true');args=parser.parse_args()
out=Path('artifacts/place-search-live');out.mkdir(parents=True,exist_ok=True)
headers={'User-Agent':'CabinOS_ONE/0.7.0 (+https://github.com/hundol047/HYUNDAI)','Accept':'application/json','Accept-Language':'ko,en'}
cases=[('스타벅스 판교',(37.3948,127.1112)),('성심당 대전',(36.3322,127.4348)),('Cafe Onion Anguk Seoul',(37.5755,126.9852)),('서울 중구 세종대로 110',(37.5547,126.9723)),('Café de Flore Paris',(48.8566,2.3522)),('Katz Delicatessen New York',(40.7484,-73.9857)),('スターバックス 渋谷',(35.6580,139.7016)),('Café Girondino São Paulo',(-23.5505,-46.6333)),('Truth Coffee Cape Town',(-33.9249,18.4241)),('Pancakes on the Rocks Sydney',(-33.8730,151.2060)),('CabinOS nonexistent shop zxq98765 Seoul',(37.5547,126.9723))]
searches=[];routes=[]
def distance(a,b):
 p1,p2=math.radians(a[0]),math.radians(b[0]);h=math.sin((p2-p1)/2)**2+math.cos(p1)*math.cos(p2)*math.sin(math.radians(b[1]-a[1])/2)**2
 return 6371000*2*math.asin(math.sqrt(min(1,max(0,h))))
def get(url,filename,allow_rejected=False):
 started=time.monotonic()
 try:response=urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=15)
 except urllib.error.HTTPError as e:
  if not allow_rejected:raise
  response=e
 with response as r:
  payload=r.read(12000001);assert len(payload)<=12000000
  body=json.loads(payload);status=r.code
 (out/filename).write_bytes(payload)
 return body,{'httpStatus':status,'elapsedMs':round((time.monotonic()-started)*1000,1),'sha256':hashlib.sha256(payload).hexdigest(),'capture':filename}
def road(name,start,end,filename,expected='available'):
 try:
  body,evidence=get(f'https://router.project-osrm.org/route/v1/driving/{start[1]},{start[0]};{end[1]},{end[0]}?overview=full&geometries=geojson&steps=true',filename,True)
  row=dict(name=name,origin=start,destination=end,expected=expected,**evidence)
  if body.get('code')!='Ok':row.update(result='unavailable' if body.get('code') in ['NoRoute','NoSegment'] else 'request_rejected',providerCode=body.get('code'))
  else:
   r=body['routes'][0];p=r['geometry']['coordinates'];a=distance(start,(p[0][1],p[0][0]));b=distance(end,(p[-1][1],p[-1][0]));steps=[s for leg in r['legs'] for s in leg['steps']]
   row.update(result='available' if a<=1000 and b<=1000 else 'snap_rejected',distanceMeters=r['distance'],geometryPoints=len(p),maneuvers=len(steps),originAccessMeters=round(a,1),destinationAccessMeters=round(b,1),syntheticGpsStartAtOriginPossible=a<=45,traffic='not provided')
 except Exception as e:row=dict(name=name,result='request_failed',reason=str(e),expected=expected)
 routes.append(row);print(json.dumps(row,ensure_ascii=False),flush=True);time.sleep(1.6)
def main():
 for i,(q,origin) in enumerate(cases):
  try:
   capture=out/f'search-{i}.json'
   if args.reuse_search_capture and capture.exists():
    payload=capture.read_bytes();body=json.loads(payload);evidence={'capture':capture.name,'sha256':hashlib.sha256(payload).hexdigest(),'transport':'replay of this session real HTTP response; not a new request'}
   else:
    params=dict(format='jsonv2',limit=6,addressdetails=1,namedetails=1,extratags=1,q=q)
    body,evidence=get('https://nominatim.openstreetmap.org/search?'+urllib.parse.urlencode(params),capture.name);time.sleep(1.6)
   row=dict(query=q,result='found' if body else 'not_found',count=len(body),**evidence)
   if body:
    chosen=min(body,key=lambda p:distance(origin,(float(p['lat']),float(p['lon']))))
    row.update(selectedId=str(chosen.get('place_id','')),selectedName=chosen.get('name') or chosen['display_name'],selectedAddress=chosen['display_name'])
    searches.append(row);road(q,origin,(float(chosen['lat']),float(chosen['lon'])),f'route-shop-{i}.json');routes[-1]['selectedId']=row['selectedId'];routes[-1]['searchCapture']=capture.name
   else:searches.append(row)
   print(json.dumps(row,ensure_ascii=False),flush=True)
  except Exception as e:searches.append(dict(query=q,result='request_failed',reason=str(e)))
 # Explicit examples of connected international roads, a disconnected ocean trip,
 # and a snapped origin far from any road. Never invent a straight-line route.
 road('Paris to Brussels',(48.8566,2.3522),(50.8466,4.3528),'route-international.json')
 road('New York to London',(40.7484,-73.9857),(51.5074,-.1278),'route-ocean.json','unavailable')
 road('Ocean origin to Paris',(0.0,0.0),(48.8566,2.3522),'route-origin-snap.json','unavailable')
 report={'type':'limited actual business search and road routing sample; not exhaustive coverage or physical GPS testing','timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'searchReplay':args.reuse_search_capture,'searches':searches,'routes':routes,'allRoutesGuaranteed':False,'allBusinessesGuaranteed':False,'commercialProviderLiveValidation':'not performed; account/key not configured'}
 Path('test-fixtures/validation/place-route-coverage.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print('Finished bounded coverage sample; inspect not_found, unavailable and snap_rejected separately.')

if __name__=="__main__":main()
