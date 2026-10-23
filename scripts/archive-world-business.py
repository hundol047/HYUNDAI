#!/usr/bin/env python3
"""Archive supplemental live business-selection drives separately from the five-hour soak."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reports', nargs='+', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
output = ROOT / args.output
output.mkdir(parents=True, exist_ok=True)
summary = {'scope': 'Supplemental actual business-search selection drives with live providers and 1 Hz synthetic GPS. Excluded from the five-hour elapsed-time/count totals.', 'cases': []}
for source in args.reports:
    file = ROOT / source
    report = json.loads(file.read_text())
    for row in report['scenarios']:
        assert row['destinationMode'] == 'provider-search-result'
        directory = file.parent / row['id']
        target = output / row['id']
        target.mkdir(exist_ok=True)
        samples = row.pop('samples')
        trace = ''.join(json.dumps(s, ensure_ascii=False, separators=(',', ':')) + '\n' for s in samples).encode()
        (target / 'samples.jsonl.gz').write_bytes(gzip.compress(trace, mtime=0))
        row['samplesFile'] = 'samples.jsonl.gz'
        row['movingGpsSamples'] = len(samples)
        for name in ['route.json', 'search.json', 'map-detail.png', 'failure.png']:
            if (directory / name).is_file():
                shutil.copy2(directory / name, target / name)
        for point in row['checkpoints']:
            if point.pop('capture', None):
                point['captureArchived'] = False
        if 'video' in row:
            row.pop('video')
            row['videoArchived'] = False
        compact = {**report, 'scenarios': [row]}
        (target / 'results.json').write_text(json.dumps(compact, ensure_ascii=False, indent=2) + '\n')
        summary['cases'].append({'id': row['id'], 'country': row['country'], 'continent': row['continent'], 'result': row['result'], 'selectedPlace': row['selectedPlace'], 'routeMeters': row['route']['distanceMeters'], 'movingGpsSamples': len(samples), 'drivingWallSeconds': row.get('drivingWallSeconds'), 'engineSha256': report['engineSha256'], 'evidence': row['id'] + '/results.json'})
summary.update(passedRoutes=sum(r['result'] == 'passed' for r in summary['cases']), countries=sorted({r['country'] for r in summary['cases']}), continents=sorted({r['continent'] for r in summary['cases']}))
(output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
(output / 'ATTRIBUTION.txt').write_text('© OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright\nLive Nominatim, OSRM and OpenStreetMap viewport tile requests at the recorded timestamps. Not an exhaustive business catalog or physical GPS test.\n')
files = sorted(p for p in output.rglob('*') if p.is_file() and p.name != 'SHA256SUMS')
(output / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(output)}\n' for p in files))
print(json.dumps({k: v for k, v in summary.items() if k != 'cases'}))
