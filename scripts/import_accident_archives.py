#!/usr/bin/env python3
"""Import latest published periods using the official code table, never ID-prefix guesses."""
import argparse
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import re
import zipfile

KINDS={'17_25_lg.csv':('municipal','지자체별'), '19_25_pedstrians.csv':('pedestrian','보행자'),
       '12_25_child.csv':('child_pedestrian','보행어린이'), '13_25_freezing.csv':('icy','결빙')}
SOURCE='https://opendata.koroad.or.kr/'


def period(label, expected):
    match=re.fullmatch(r'(\d{2})(?:-(\d{2}))?년 '+re.escape(expected)+r' 교통사고 다발지역',label)
    if not match:raise ValueError('Unrecognized official statistical period')
    start=2000+int(match[1]);end=2000+int(match[2] or match[1])
    if not 2010<=start<=end<=2100:raise ValueError('Invalid statistical period')
    return start,end


def geometry(raw, lat, lon):
    g=json.loads(raw)
    if g.get('type') not in ('Polygon','MultiPolygon'):raise ValueError('Unsupported geometry')
    polys=[g['coordinates']] if g['type']=='Polygon' else g['coordinates']
    if not polys or len(polys)>32:raise ValueError('Invalid geometry components')
    radius=0;count=0
    for poly in polys:
        if not poly or len(poly)>32:raise ValueError('Invalid rings')
        for ring in poly:
            if len(ring)<4 or ring[0]!=ring[-1]:raise ValueError('Unclosed ring')
            for point in ring:
                if len(point)!=2:raise ValueError('Invalid point')
                x,y=point
                if not math.isfinite(x) or not math.isfinite(y) or not 124<=x<=132 or not 33<=y<=39:raise ValueError('Invalid coordinates')
                radius=max(radius,math.hypot((y-lat)*111195,(x-lon)*111195*math.cos(math.radians(lat))))
                count+=1
    if count>2048 or not 10<=radius<=1500:raise ValueError('Unexpected geometry extent')
    return g,math.ceil(radius)


def convert(rows,kind,title,mapping,expires):
    periods={r['사고다발지id']:period(mapping[r['사고다발지id']],title) for r in rows}
    latest=max(periods.values(),key=lambda p:(p[1],p[0]))
    seen={};skipped=0;older=0;out=[]
    for r in rows:
        start,end=periods[r['사고다발지id']]
        lat,lon=float(r['위도']),float(r['경도'])
        if not math.isfinite(lat) or not math.isfinite(lon) or not 33<=lat<=39 or not 124<=lon<=132:raise ValueError('Invalid center')
        g,radius=geometry(r['다발지역폴리곤'],lat,lon)
        if (start,end)!=latest:older+=1;continue
        fid=r['사고다발지fid'].strip()
        if not fid.isdecimal():raise ValueError('Invalid official ID')
        row=dict(id=f'koroad-{kind}-{fid}',lat=lat,lon=lon,radiusMeters=radius,year=end,
                 periodStart=start,periodEnd=end,kind=kind,datasetId=r['사고다발지id'],
                 source=SOURCE,validUntilMs=expires,geometry=g)
        if row['id'] in seen:
            if seen[row['id']]!=row:raise ValueError('Conflicting duplicate')
            skipped+=1;continue
        seen[row['id']]=row;out.append(row)
    return out,dict(inputRows=len(rows),selectedRows=len(out),olderPeriodRows=older,identicalDuplicateRows=skipped,
                   periodStart=latest[0],periodEnd=latest[1],datasetIds=sorted({r['datasetId'] for r in out}))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('archives',nargs='+',type=Path);p.add_argument('--periods',type=Path,required=True)
    p.add_argument('--expires-ms',type=int,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--audit',type=Path,required=True);a=p.parse_args()
    mapping=json.loads(a.periods.read_text())['datasetPeriods'];zones=[];audit={'files':{},'archives':[]};found=set()
    for archive in a.archives:
        audit['archives'].append({'name':archive.name,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()})
        with zipfile.ZipFile(archive) as z:
            if sum(f.file_size for f in z.infolist())>50_000_000:raise ValueError('Archive too large')
            for name in z.namelist():
                if name not in KINDS:continue
                if name in found:raise ValueError('Repeated input file')
                found.add(name)
                rows=list(csv.DictReader(io.StringIO(z.read(name).decode('cp949'))))
                kind,title=KINDS[name]
                batch,details=convert(rows,kind,title,mapping,a.expires_ms)
                zones+=batch;audit['files'][kind]=details
    if found!=set(KINDS):raise ValueError('All four location datasets are required')
    encoded=(json.dumps(zones,ensure_ascii=False,separators=(',',':'))+'\n').encode()
    audit.update(zoneCount=len(zones),sha256=hashlib.sha256(encoded).hexdigest(),validUntilMs=a.expires_ms,
                 selection='Latest verified statistical period per category; no cross-category accident count summation',
                 excludedStatisticsFile='10_25_stt.csv contains no coordinates')
    a.output.write_bytes(encoded);a.audit.write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(audit,ensure_ascii=False))


if __name__=='__main__':main()
