#!/usr/bin/env python3
"""Report raw parked signal timing; UNKNOWN alone never implies a parked vehicle."""
import argparse
import json
import re


def analyze(text):
    rows=[];groups=[];current=[]
    for line in text.splitlines():
        values=dict(re.findall(r'(\w+)=([^\s]+)',line))
        if 'gear' not in values: continue
        if values.get('gear')!='PARK' or values.get('speedKph') not in ('0','0.0'):
            if current: groups.append(current);current=[]
            continue
        try:
            row={k:float(values[k]) for k in ('speedAgeMs','lastSpeedCallbackElapsedMs','lastPollSuccessElapsedMs','gearAgeMs','gearReadAgeMs')}
            if all(v>=0 for v in row.values()): rows.append(row);current.append(row)
        except (KeyError,ValueError):
            if current: groups.append(current);current=[]
    if current: groups.append(current)
    periods=[[r for r in group if r['speedAgeMs']>3000] for group in groups]
    periods=[group for group in periods if group]
    stale=[r for group in periods for r in group]
    def progressed(key): return bool(periods) and all(len(group)>1 and all(b[key]>a[key] for a,b in zip(group,group[1:])) for group in periods)
    poll=progressed('lastPollSuccessElapsedMs');callback=progressed('lastSpeedCallbackElapsedMs')
    sufficient=bool(rows) and (not stale or len(stale)>1)
    diagnosis=('VHAL speed timestamp not refreshed while parked' if poll and callback else 'callbacks/polls stopped') if stale or not rows else 'ok'
    return dict(diagnosis=diagnosis,sufficientEvidence=sufficient,parkedSamples=len(rows),staleSamples=len(stale),
                maxSpeedAgeMs=max((r['speedAgeMs'] for r in rows),default=None),
                maxGearAgeMs=max((r['gearAgeMs'] for r in rows),default=None),
                maxGearReadAgeMs=max((r['gearReadAgeMs'] for r in rows),default=None),
                pollContinued=poll,callbackContinued=callback,
                fallbackEvidence=sufficient and poll and callback and all(r['gearReadAgeMs']<1000 for r in stale))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('log');args=parser.parse_args()
    with open(args.log) as file: report=analyze(file.read())
    print(json.dumps(report,indent=2));print(report['diagnosis'])
