"""Validated public historical areas, kept separate from private community observations."""
import hashlib
import json
import math
from pathlib import Path

KINDS={'municipal','pedestrian','child_pedestrian','icy'}


def finite(value, low, high):
    if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or not low<=value<=high:
        raise ValueError('Invalid numeric field')


def validate_geometry(g):
    if not isinstance(g,dict) or set(g)!={'type','coordinates'} or g['type'] not in ('Polygon','MultiPolygon'):
        raise ValueError('Invalid area geometry')
    polys=[g['coordinates']] if g['type']=='Polygon' else g['coordinates']
    if not isinstance(polys,list) or not 1<=len(polys)<=32:raise ValueError('Invalid components')
    count=0
    for poly in polys:
        if not isinstance(poly,list) or not 1<=len(poly)<=32:raise ValueError('Invalid rings')
        for ring in poly:
            if not isinstance(ring,list) or len(ring)<4 or ring[0]!=ring[-1]:raise ValueError('Unclosed area ring')
            for point in ring:
                if not isinstance(point,list) or len(point)!=2:raise ValueError('Invalid point')
                finite(point[0],124,132);finite(point[1],33,39);count+=1
    if count>2048:raise ValueError('Area too complex')


def load_zones(path, now):
    if not path:return []
    with open(path,'rb') as f:raw=f.read(5_000_001)
    if len(raw)>5_000_000:raise ValueError('Dataset too large')
    rows=json.loads(raw)
    if not isinstance(rows,list) or len(rows)>5000:raise ValueError('Invalid zones')
    base={'id','lat','lon','radiusMeters','year','source','validUntilMs'}
    extended={'periodStart','periodEnd','kind','datasetId','geometry'};seen=set()
    for r in rows:
        if set(r) not in (base,base|extended):raise ValueError('Invalid zone schema')
        finite(r['lat'],-85,85);finite(r['lon'],-180,180);finite(r['radiusMeters'],10,1500)
        finite(r['year'],2020,2100);finite(r['validUntilMs'],0,now+366*86400000)
        if not isinstance(r['source'],str) or not r['source'].startswith('https://') or len(r['source'])>300:raise ValueError('Source required')
        if not isinstance(r['id'],str) or not 1<=len(r['id'])<=80 or r['id'] in seen:raise ValueError('Duplicate or invalid ID')
        seen.add(r['id'])
        if 'geometry' in r:
            if r['kind'] not in KINDS or not isinstance(r['datasetId'],str) or not r['datasetId'].isdecimal():raise ValueError('Invalid category')
            finite(r['periodStart'],2010,r['year']);finite(r['periodEnd'],r['year'],r['year'])
            validate_geometry(r['geometry'])
    return rows


def metadata(rows, now, digest=None):
    current=[r for r in rows if r['validUntilMs']>now];groups={}
    for r in current:
        key=r.get('kind','legacy');group=groups.setdefault(key,{'count':0,'periodStart':r.get('periodStart',r['year']),'periodEnd':r['year']})
        group['count']+=1
    if digest is None:digest=hashlib.sha256((json.dumps(rows,ensure_ascii=False,separators=(',',':'))+'\n').encode()).hexdigest()
    return {'historicalOnly':True,'zoneCount':len(current),'loadedRows':len(rows),'sha256':digest,'categories':groups,
            'source':'한국도로교통공단 교통사고정보 개방시스템','note':'Historical statistics; not live accidents or current ice conditions'}
