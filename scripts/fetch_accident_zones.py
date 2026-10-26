#!/usr/bin/env python3
"""Import KOROAD historical frequent-zone data; requires a separately issued API key."""
import argparse
import json
import math
import os
from pathlib import Path
import time
import urllib.parse
import urllib.request

SOURCE = 'https://opendata.koroad.or.kr/api/selectLgDataSet.do'
ENDPOINT = 'https://opendata.koroad.or.kr/data/rest/frequentzone/lg'


def convert(item, year, now):
    lat, lon = float(item['la_crd']), float(item['lo_crd'])
    if not (33 <= lat <= 39 and 124 <= lon <= 132):
        raise ValueError('Invalid Korean coordinates')
    geom = json.loads(item['geom_json'])
    if geom['type'] != 'Polygon':
        raise ValueError('Expected official polygon')
    ring = geom['coordinates'][0]
    if not 4 <= len(ring) <= 10000:
        raise ValueError('Invalid polygon size')
    distances = []
    for x, y in ring:
        if not (33 <= y <= 39 and 124 <= x <= 132):
            raise ValueError('Invalid polygon coordinate')
        distances.append(math.hypot((y-lat)*111195, (x-lon)*111195*math.cos(math.radians(lat))))
    radius = math.ceil(max(distances))
    if not 10 <= radius <= 1000:
        raise ValueError('Unexpected official polygon extent')
    ident = str(item['afos_fid'])
    if not ident.isdecimal() or len(ident) > 30:
        raise ValueError('Invalid official identifier')
    return dict(id='koroad-'+ident, lat=lat, lon=lon, radiusMeters=radius,
                year=year, source=SOURCE, validUntilMs=now+90*86400000)


def fetch(key, year, sido, sigungu):
    rows, seen = [], set()
    now = int(time.time()*1000)
    for page in range(1, 51):
        if page > 1:
            time.sleep(1.1)
        query = urllib.parse.urlencode(dict(authKey=key, searchYearCd=year, siDo=sido,
            guGun=sigungu, type='json', numOfRows=100, pageNo=page))
        req = urllib.request.Request(ENDPOINT+'?'+query, headers={'Accept':'application/json'})
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise ValueError('Response too large')
        data = json.loads(raw)
        if data.get('resultCode') != '00':
            raise ValueError('API did not return success')
        total = int(data['totalCount'])
        if not 0 <= total <= 5000:
            raise ValueError('Invalid record count')
        items = data.get('items') or {}
        batch = items.get('item') or []
        if isinstance(batch, dict):
            batch = [batch]
        if not isinstance(batch, list) or len(batch) > 100:
            raise ValueError('Invalid page')
        for item in batch:
            row = convert(item, year, now)
            if row['id'] in seen:
                raise ValueError('Duplicate official record')
            seen.add(row['id']); rows.append(row)
        if len(rows) == total:
            return rows
        if not batch or len(rows) > total:
            raise ValueError('Incomplete pagination')
    raise ValueError('Pagination limit exceeded')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--year', type=int, required=True)
    p.add_argument('--sido', required=True)
    p.add_argument('--sigungu', required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    key = os.environ.get('KOROAD_API_KEY', '')
    if not key:
        p.exit(2, 'KOROAD_API_KEY is missing; no request was sent.\n')
    if not (2020 <= a.year <= time.gmtime().tm_year and len(a.sido)==2 and a.sido.isdecimal()
            and len(a.sigungu)==3 and a.sigungu.isdecimal()):
        p.exit(2, 'Invalid year or administrative codes.\n')
    try:
        rows = fetch(key, a.year, a.sido, a.sigungu)
        # Never log exception URLs: they can contain authKey.
        a.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2)+'\n')
    except Exception:
        p.exit(1, 'Import failed; check API authorization, region/year and official schema. No partial file saved.\n')
    print(f'Imported {len(rows)} historical zones for {a.year}; not live accident reports.')


if __name__ == '__main__':
    main()
