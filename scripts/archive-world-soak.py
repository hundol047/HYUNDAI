#!/usr/bin/env python3
"""Archive measured live-audit evidence without inflating partial routes into passes."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return json.loads(path.read_text())


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def percentile(values, fraction):
    values = sorted(values)
    return values[min(len(values) - 1, int(len(values) * fraction))] if values else None


def archive(source, output, allow_running=False):
    state = read(source / 'status.json')
    if not allow_running and (state['status'] != 'completed' or state['runningSeconds'] < state['targetSeconds']):
        raise SystemExit('Audit has not completed its requested elapsed time. Use --allow-running only for an explicitly partial report.')
    output.mkdir(parents=True, exist_ok=True)
    cases = read(source / 'cases.json')
    catalog = {row['id']: row for row in cases}
    summaries, reports, update_times = [], [], []
    for number, attempt in enumerate(state['attempts'], 1):
        case = catalog[attempt['id']]
        directory = ROOT / attempt['directory']
        report_file = directory / 'results.json'
        item = {**case, 'attempt': number, 'result': attempt['status'], 'startedAt': attempt['startedAt'],
                'finishedAt': attempt.get('finishedAt'), 'movingGpsSamples': 0, 'distanceMeters': 0}
        if not report_file.exists():
            item['failure'] = attempt.get('failure', 'No scenario report was saved')
            summaries.append(item)
            continue
        report = read(report_file)
        if not report.get('scenarios'):
            item['failure'] = 'No scenario was saved'
            summaries.append(item)
            continue
        row = report['scenarios'][-1]
        samples = row.pop('samples', [])
        item.update(result=row['result'], movingGpsSamples=len(samples), gpsCallbacks=row.get('gpsCallbacks'),
                    distanceMeters=samples[-1]['atMeters'] if samples else 0,
                    drivingWallSeconds=row.get('drivingWallSeconds', 0),
                    mapChecks=len(row['checkpoints']), mapCheckTimeouts=sum(bool(p['timeout']) for p in row['checkpoints']),
                    continuousMapHealthRecorded='mapCoverageGaps' in row,
                    sustainedMapGaps=row.get('mapCoverageGaps'),
                    offRouteSamples=sum(bool(p.get('offRoute')) for p in samples),
                    untrackedSamples=sum(not p.get('tracking', False) for p in samples),
                    arrived=row.get('finalState', {}).get('arrived', False),
                    searchQuery=row['query'], searchResult=row['search'].get('result'),
                    searchCount=row['search'].get('uiCount', 0),
                    searchCountryFlags=row['search'].get('outsideExpectedCountry', []),
                    searchDistanceFlags=row['search'].get('distantResults', []),
                    engineSha256=report.get('engineSha256'), siteManifest=report.get('siteManifest'),
                    failure=row.get('failure'), destinationMode=row.get('destinationMode', 'explicit-coordinate'))
        timings = [s['updateMs'] for s in samples if isinstance(s.get('updateMs'), (int, float))]
        update_times.extend(timings)
        item['gpsUiUpdateP95Ms'] = percentile(timings, .95)
        key = f"{number:03d}-{row['id']}"
        target = output / key
        target.mkdir(exist_ok=True)
        captured = directory / row['id']
        for filename in ['route.json', 'search.json']:
            if (captured / filename).is_file():
                shutil.copy2(captured / filename, target / filename)
        # One detailed map plus failures are enough for a reviewable archive.
        # Other full-resolution checkpoints remain in the original local audit.
        images = {'map-detail.png', 'failure.png'}
        images.update(p.name for p in captured.glob('map-gap-*.png'))
        images.update(p['capture'] for p in row['checkpoints'] if p['timeout'] and p.get('capture'))
        for filename in images:
            if (captured / filename).is_file():
                shutil.copy2(captured / filename, target / filename)
        for point in row['checkpoints']:
            name = point.pop('capture', None)
            if name and (target / name).is_file():
                point['capture'] = name
            elif name:
                point['captureArchived'] = False
        trace = ''.join(json.dumps(s, ensure_ascii=False, separators=(',', ':')) + '\n' for s in samples).encode()
        (target / 'samples.jsonl.gz').write_bytes(gzip.compress(trace, mtime=0))
        row['samplesFile'] = 'samples.jsonl.gz'
        row['movingGpsSamples'] = len(samples)
        # The original video is local unless separately published as a named MP4.
        if 'video' in row:
            row['videoArchived'] = False
            row.pop('video')
        write(target / 'results.json', report)
        item['evidence'] = key + '/results.json'
        if (target / 'map-detail.png').is_file():
            item['mapImage'] = key + '/map-detail.png'
        summaries.append(item)
        reports.append(report)
    finished = [r for r in summaries if r['result'] == 'passed']
    summary = {
        'scope': state['scope'], 'status': state['status'],
        'complete': state['status'] == 'completed' and state['runningSeconds'] >= state['targetSeconds'],
        'archivedAt': datetime.now(timezone.utc).isoformat(),
        'startedAt': state['startedAt'], 'observedThrough': state['updatedAt'],
        'targetSeconds': state['targetSeconds'], 'runningSeconds': state['runningSeconds'],
        'candidateRoutes': len(cases), 'candidateCountries': len({r['country'] for r in cases}),
        'resultCounts': dict(Counter(r['result'] for r in summaries)),
        'passedRoutes': len(finished), 'passedCountries': sorted({r['country'] for r in finished}),
        'passedContinents': sorted({continent for r in finished for continent in r['continent'].split('/')}),
        'unattempted': [r['id'] for r in cases if r['id'] not in {s['id'] for s in summaries}],
        'movingGpsSamples': sum(r['movingGpsSamples'] for r in summaries),
        'observedDistanceMeters': sum(r['distanceMeters'] for r in summaries),
        'drivingWallSeconds': sum(r.get('drivingWallSeconds', 0) for r in summaries),
        'mapChecks': sum(r.get('mapChecks', 0) for r in summaries),
        'mapCheckTimeouts': sum(r.get('mapCheckTimeouts', 0) for r in summaries),
        'offRouteSamples': sum(r.get('offRouteSamples', 0) for r in summaries),
        'untrackedSamples': sum(r.get('untrackedSamples', 0) for r in summaries),
        'gpsUiUpdateP95Ms': percentile(update_times, .95),
        'continuousMapHealthCases': sum(bool(r.get('continuousMapHealthRecorded')) for r in summaries),
        'sustainedMapGaps': sum(len(r.get('sustainedMapGaps') or []) for r in summaries),
        'searchCounts': dict(Counter(r.get('searchResult', 'not-run') for r in summaries)),
        'cases': summaries,
        'limitations': [
            'Elapsed audit time includes preparation, search and map checkpoints; moving time is reported separately.',
            'Only measured samples are counted. Deadline-incomplete and failed routes are never counted as passes.',
            'Synthetic GPS in Chromium, not a physical GPS, road legality, traffic, store completeness or device FPS certification.',
            'A decoded tile proves rendering, not a complete or current road/business catalog.',
            'Country and >50km search flags require review; a namesake result is not automatically a provider error.',
            'Initial cases may not contain continuous map-health records; the per-case field states availability.',
            'Intermediate builds are identified by engine hashes and site manifests; results are not all from the final build.',
        ],
    }
    write(output / 'summary.json', summary)
    write(output / 'status.json', state)
    write(output / 'cases.json', cases)
    if (source / 'site-switches.json').exists():
        shutil.copy2(source / 'site-switches.json', output / 'site-switches.json')
    (output / 'ATTRIBUTION.txt').write_text('Map and geospatial data: © OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright\nLive services: Nominatim, OSRM public routing, OpenStreetMap raster tiles. Captured at the per-case timestamps; not an exhaustive or authoritative catalog.\n')
    files = sorted(p for p in output.rglob('*') if p.is_file() and p.name != 'SHA256SUMS')
    (output / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(output)}\n' for p in files))
    print(json.dumps({k: summary[k] for k in ['complete', 'runningSeconds', 'resultCounts', 'passedCountries', 'movingGpsSamples', 'mapCheckTimeouts']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--allow-running', action='store_true')
    args = parser.parse_args()
    archive((ROOT / args.input).resolve(), (ROOT / args.output).resolve(), args.allow_running)
