#!/usr/bin/env python3
"""Manual Console driving with automated, conservative log evidence verdicts."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

STEPS = dict(park='P / 0, wait at least 3 seconds', focus='Set waiting 30 min; start focus 15 min',
             traffic='D / 0; session pauses', drive='D / 40 km/h; parking plan clears',
             repark='P / 0, wait at least 3 seconds',
             soc='Set SOC below target; start session; then cross target',
             arrival='Set stock navigation destination and arrival target in CabinOS')


def events(text):
    result=[]
    for line in text.splitlines():
        try:
            value=json.loads(line[line.index('{'):])
            if value.get('event')=='vehicle_read_state': result.append(value)
        except (ValueError, TypeError): pass
    return result


def verdict(step, rows):
    if not rows or any(r.get('source')!='VEHICLE' or
        (r.get('parkedAllowed') and (not r.get('connected') or r.get('gear')!='PARK' or r.get('speedKph')!=0)) for r in rows):
        return False
    def parked(r): return r.get('connected') and r.get('gear')=='PARK' and r.get('speedKph')==0
    if step in ('park','repark'):
        # Missing/interrupted evidence cannot prove a continuous stationary interval.
        return all(parked(r) for r in rows) and rows[-1].get('parkedAllowed') is True and \
            rows[-1]['atMs']-rows[0]['atMs']>=3000 and all(0<=b['atMs']-a['atMs']<=1500 for a,b in zip(rows,rows[1:]))
    if step=='focus': return any(r.get('sessionKind')=='FOCUS' and r.get('sessionDurationSeconds')==900 and r.get('sessionStatus')=='RUNNING' and r.get('parkedAllowed') for r in rows)
    if step=='traffic': return any(r.get('gear')=='DRIVE' and r.get('speedKph')==0 and r.get('context')=='STOPPED_IN_TRAFFIC' and r.get('sessionStatus')=='PAUSED' and not r.get('parkedAllowed') for r in rows)
    if step=='drive': return any(r.get('gear')=='DRIVE' and r.get('speedKph',0)>0 and r.get('context')=='DRIVING' and not r.get('parkedAllowed') and 'departureAtMs' in r and r['departureAtMs'] is None for r in rows)
    if step=='soc':
        for i,a in enumerate(rows):
            target=a.get('chargeTargetPercent')
            if target is None or a.get('sdkBatteryPercent') is None or not a.get('sdkSocUsed') or a['sdkBatteryPercent']>=target or a.get('sessionStatus')!='RUNNING': continue
            if any(b.get('sdkSocUsed') and b.get('sdkBatteryPercent',-1)>=target and b.get('chargeTargetPercent')==target and b.get('sessionStatus')=='COMPLETED' and b.get('context')=='PREPARING_DEPARTURE' and 'SOC' in b.get('planSource','') for b in rows[i+1:]): return True
        return False
    if step=='arrival': return any(r.get('arrivalTargetMs') and r.get('navigationReadAtMs') and 0<=r['atMs']-r['navigationReadAtMs']<=3000 and 'NaviHelper' in r.get('planSource','') and r.get('departureAtMs') is not None for r in rows)
    return False


def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',default='emulator-5554');p.add_argument('--apk',required=True);p.add_argument('--output',required=True)
    args=p.parse_args();out=Path(args.output);out.mkdir(parents=True,exist_ok=False)
    adb=['adb','-s',args.serial]
    def run(*a): return subprocess.check_output(adb+list(a),text=True).strip()
    digest=hashlib.sha256(Path(args.apk).read_bytes()).hexdigest()
    fingerprint=run('shell','getprop','ro.build.fingerprint');(out/'fingerprint.txt').write_text(fingerprint+'\n')
    if 'release_connect_v2.1.0' not in fingerprint or run('shell','getprop','ro.build.version.sdk')!='34': raise SystemExit('Official Pleos 2.1.0 / API 34 required')
    run('install','-r','-g',args.apk);run('forward','tcp:8088','tcp:8088')
    installed=run('shell','pm','path','io.cabinos.one').splitlines()
    base=next((x.removeprefix('package:') for x in installed if x.endswith('/base.apk')),None)
    if not base: raise SystemExit('Installed base APK path missing')
    run('pull',base,str(out/'installed.apk'));installed_hash=hashlib.sha256((out/'installed.apk').read_bytes()).hexdigest()
    (out/'apk-sha256.json').write_text(json.dumps(dict(input=digest,installed=installed_hash),indent=2))
    if installed_hash!=digest: raise SystemExit('Installed APK hash mismatch')
    run('shell','setprop','log.tag.CabinVehicle','DEBUG');run('logcat','-c')
    log=(out/'vehicle.log').open('w');capture=subprocess.Popen(adb+['logcat','-v','threadtime','-s','CabinVehicle:D','*:S'],stdout=log)
    remote='/sdcard/cabinos-full-scenario.mp4'
    recording=subprocess.Popen(adb+['shell','screenrecord','--time-limit','120',remote])
    started=time.monotonic();results={};offset=0
    try:
        run('shell','am','force-stop','io.cabinos.one');run('shell','am','start','-n','io.cabinos.one/.MainActivity')
        print('Open http://localhost:8088. Console endpoints are unconfirmed: perform all vehicle changes manually. Recording ends at 120 seconds.')
        for step,instruction in STEPS.items():
            input(f'{step}: {instruction}. Press Enter AFTER the action; evidence determines the verdict. ')
            text=(out/'vehicle.log').read_text();rows=events(text[offset:]);offset=len(text)
            ok=bool(verdict(step,rows)) and time.monotonic()-started<=120 and recording.poll() is None
            results[step]=dict(passed=ok,eventCount=len(rows),reason='log evidence matched' if ok else 'missing/mismatched evidence or recording expired')
            (out/f'{step}.json').write_text(json.dumps(rows,indent=2));print(results[step])
    finally:
        # Complete the single recording; never concatenate screenshots or clips.
        recording.wait();capture.terminate();capture.wait();log.close()
        run('pull',remote,str(out/'continuous.mp4'))
        for step in STEPS: results.setdefault(step,dict(passed=False,reason='step not executed'))
        (out/'verdict.json').write_text(json.dumps(dict(steps=results,allPassed=all(x['passed'] for x in results.values()),apkHash=digest),indent=2))
    return 0 if all(x['passed'] for x in results.values()) else 1

if __name__=='__main__': raise SystemExit(main())
