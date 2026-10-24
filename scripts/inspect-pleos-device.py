#!/usr/bin/env python3
"""Collect actual official-emulator diagnostics; never marks a submission complete."""
import argparse
import json
import os
import shutil
import sys
import subprocess
import time
import urllib.request
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
adb_path = shutil.which('adb') or str(Path(os.environ.get('ANDROID_HOME',''))/'platform-tools/adb')
if not Path(adb_path).is_file():
    raise SystemExit('::error::ADB executable unavailable; set ANDROID_HOME/platform-tools on PATH')
adb = [adb_path, '-s', a.serial]
def report_error(kind, value, trace):
    message=(kind.__name__+': '+str(value)).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
    print('::error::'+message[:1500],flush=True)
    sys.__excepthook__(kind,value,trace)
sys.excepthook=report_error
def run(*args, timeout=30, check=True):
    return subprocess.run(adb+list(args), text=True, capture_output=True, timeout=timeout, check=check)

start = time.monotonic()
while time.monotonic()-start < 600:
    try:
        if run('shell','getprop','sys.boot_completed',timeout=10,check=False).stdout.strip() == '1':
            break
    except subprocess.TimeoutExpired:
        pass
    print('Waiting for actual Pleos boot:', round(time.monotonic()-start), 's', flush=True)
    time.sleep(10)
else:
    raise SystemExit('Official image did not finish booting within 600 seconds')
identity = run('shell','getprop','ro.build.fingerprint').stdout.strip()
if 'Pleos/' not in identity or 'release_connect_v2.1.0' not in identity:
    raise SystemExit('Unexpected image fingerprint: '+identity)
print('Official image fingerprint:', identity, flush=True)
run('forward','tcp:8088','tcp:8088')
for endpoint in ['/', '/vehicle/catalog', '/vehicle/state']:
    with urllib.request.urlopen('http://127.0.0.1:8088'+endpoint, timeout=15) as r:
        data = r.read()
    (a.output / ('console'+endpoint.replace('/','-')+('.html' if endpoint=='/' else '.json'))).write_bytes(data)
run('install','-r','-g',str(a.apk),timeout=120)
run('shell','setprop','log.tag.CabinVehicle','INFO')
run('shell','am','start','-W','-n','io.cabinos.one/.MainActivity',timeout=90)
time.sleep(8)
run('shell','uiautomator','dump','/sdcard/cabinos-inspect.xml',timeout=45)
run('pull','/sdcard/cabinos-inspect.xml',str(a.output/'screen.xml'))
with (a.output/'screen.png').open('wb') as f:
    subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
(a.output/'vehicle-logcat.txt').write_text(run('logcat','-d','-v','threadtime','CabinVehicle:I','*:S').stdout)
(a.output/'crashes.txt').write_text(run('logcat','-d','-b','crash').stdout)
(a.output/'package.txt').write_text(run('shell','dumpsys','package','io.cabinos.one').stdout)
(a.output/'identity.json').write_text(json.dumps({'fingerprint':identity,'bootSeconds':round(time.monotonic()-start),'submissionReady':False},indent=2))
print('APK installed and actual display/vehicle logs collected. Runtime acceptance still requires review.',flush=True)
