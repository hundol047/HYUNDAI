#!/usr/bin/env python3
"""Explicit small live provider smoke check. Never substitutes synthetic responses."""
import argparse,datetime,hashlib,json,time,urllib.request,urllib.parse
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--all-regions',action='store_true');args=parser.parse_args()
artifact_dir=Path('artifacts/navigation-live');artifact_dir.mkdir(parents=True,exist_ok=True)
headers={'User-Agent':'CabinOS_ONE/0.5.0 (+https://github.com/hundol047/HYUNDAI)','Accept':'application/json','Accept-Language':'ko,en'}
rows=[];blocked=False
regions=[('Seoul Station South Korea',(126.9723,37.5547),(126.9779,37.5665)),('Paris France',(2.3522,48.8566),(2.2945,48.8584)),('New York USA',(-73.9857,40.7484),(-73.9851,40.7580)),('Tokyo Station Japan',(139.7671,35.6812),(139.7700,35.6900))]
def check(name,url,validate):
 global blocked
 host=urllib.parse.urlsplit(url).hostname
 try:
  with urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=12) as r:
   payload=r.read(12_000_001);assert len(payload)<=12_000_000
   evidence=validate(payload)
   artifact=artifact_dir/(name.lower().replace(' ','-')+('.png' if 'tile' in name.lower() else '.json'));artifact.write_bytes(payload)
   row={'name':name,'host':host,'result':'passed','httpStatus':r.status,'bytes':len(payload),'sha256':hashlib.sha256(payload).hexdigest(),'evidence':evidence}
 except Exception as e:
  message=str(e);denied='Tunnel connection failed: 403' in message or 'CONNECT' in message and '403' in message
  blocked|=denied
  row={'name':name,'host':host,'result':'blocked' if denied else 'failed','reason':message}
 rows.append(row);print(json.dumps(row,ensure_ascii=False),flush=True)
def places(data):
 items=json.loads(data);assert isinstance(items,list) and items
 assert all('lat' in p and 'lon' in p and p.get('display_name') for p in items)
 return {'places':len(items)}
def route(data):
 body=json.loads(data);assert body.get('code')=='Ok'
 r=body['routes'][0];coords=r['geometry']['coordinates'];steps=[s for l in r['legs'] for s in l['steps']]
 assert len(coords)>1 and steps and r['distance']>0 and r['duration']>0
 return {'distanceMeters':r['distance'],'durationSeconds':r['duration'],'geometryPoints':len(coords),'maneuvers':len(steps),'traffic':'not provided'}
def tile(data):
 assert data.startswith(b'\x89PNG\r\n\x1a\n');return {'format':'PNG'}
check('OSM map tile','https://tile.openstreetmap.org/0/0/0.png',tile)
for name,start,end in regions if args.all_regions else regions[:1]:
 check(name+' destination','https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q='+urllib.parse.quote(name),places)
 check(name+' driving route',f'https://router.project-osrm.org/route/v1/driving/{start[0]},{start[1]};{end[0]},{end[1]}?overview=full&geometries=geojson&steps=true',route)
 if blocked:break
 time.sleep(1.5)
report={'type':'actual external provider requests; no fixtures or alternate proxy routes','timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':rows,'physicalDeviceGps':'not tested','realRoadDriving':'not tested'}
Path('docs/validation').mkdir(parents=True,exist_ok=True);Path('docs/validation/live-navigation-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
raise SystemExit(2 if blocked else 1 if any(r['result']!='passed' for r in rows) else 0)
