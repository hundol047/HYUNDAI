#!/usr/bin/env python3
"""Collect actual official-emulator diagnostics; never marks a submission complete."""
import argparse
import json
import os
import shutil
import sys
import re
import xml.etree.ElementTree as ET
import subprocess
import time
import urllib.request
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
if not re.fullmatch(r'emulator-\d+',a.serial):
    raise SystemExit('Only an explicit emulator serial is accepted')
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

def notice(title, value):
    text=json.dumps(value,ensure_ascii=False) if not isinstance(value,str) else value
    text=text.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
    print('::notice title='+title+'::'+text[:5500],flush=True)

def boot_diagnostics():
    log=a.output/'emulator.log'
    if log.exists():
        lines=[line for line in log.read_text(errors='replace').splitlines() if any(x in line.lower() for x in ['error','failed','fatal','panic'])]
        notice('Pleos emulator startup errors','\n'.join(lines[-8:]) or 'No emulator error line captured')
    try:
        crash=run('logcat','-d','-b','crash','-t','80',timeout=10,check=False).stdout
        (a.output/'boot-crashes.txt').write_text(crash)
        notice('Pleos boot crash summary','\n'.join(line for line in crash.splitlines() if any(x in line for x in ['Cmdline:','Abort message:','FATAL EXCEPTION','Caused by:'])))
    except subprocess.TimeoutExpired:
        notice('Pleos boot diagnostics','ADB crash read timed out')

start = time.monotonic()
while time.monotonic()-start < 600:
    try:
        if run('shell','getprop','sys.boot_completed',timeout=10,check=False).stdout.strip() == '1':
            break
    except subprocess.TimeoutExpired:
        pass
    pidfile=Path(os.environ.get('RUNNER_TEMP','/tmp'))/'pleos-emulator.pid'
    if pidfile.exists():
        pid=pidfile.read_text().strip()
        process=Path('/proc')/pid/'stat'
        if not process.exists() or process.read_text().split(') ',1)[-1].startswith('Z'):
            boot_diagnostics()
            raise RuntimeError('Emulator process exited before Android boot; see startup error annotation')
    print('Waiting for actual Pleos boot:', round(time.monotonic()-start), 's', flush=True)
    time.sleep(10)
else:
    boot_diagnostics()
    raise RuntimeError('Official image did not finish booting within 600 seconds; no APK execution claimed')
identity = run('shell','getprop','ro.build.fingerprint').stdout.strip()
if 'Pleos/' not in identity or 'release_connect_v2.1.0' not in identity:
    raise RuntimeError('Unexpected image fingerprint: '+identity)
print('Official image fingerprint:', identity, flush=True)
run('forward','tcp:8088','tcp:8088')
for endpoint in ['/', '/vehicle/catalog', '/vehicle/state']:
    with urllib.request.urlopen('http://127.0.0.1:8088'+endpoint, timeout=15) as r:
        data = r.read()
    (a.output / ('console'+endpoint.replace('/','-')+('.html' if endpoint=='/' else '.json'))).write_bytes(data)
# The same public local endpoints used by the official Virtual Console UI.
# These inputs target the verified emulator, not the app's simulator or a physical car.
for endpoint in ['/vehicle/controls?speed=0&accelerator=0&brake=0','/vehicle/gear?gear=Park',
                 '/vehicle/ev-battery?field=soc&value=68','/vehicle/ev-battery?field=charge_switch&value=true']:
    with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8088'+endpoint,method='PUT'),timeout=15) as response:
        if response.status != 200: raise RuntimeError('Virtual Console rejected '+endpoint)
run('install','-r','-g',str(a.apk),timeout=120)
run('shell','setprop','log.tag.CabinVehicle','INFO')
run('shell','am','start','-W','-n','io.cabinos.one/.MainActivity',timeout=90)
time.sleep(8)
run('shell','uiautomator','dump','/sdcard/cabinos-inspect.xml',timeout=45)
run('pull','/sdcard/cabinos-inspect.xml',str(a.output/'screen.xml'))
with (a.output/'screen.png').open('wb') as f:
    subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
(a.output/'vehicle-logcat.txt').write_text(run('logcat','-d','-v','threadtime','CabinVehicle:I','*:S').stdout)
events=[]
for line in (a.output/'vehicle-logcat.txt').read_text().splitlines():
    try:
        event=json.loads(line[line.index('{'):])
        if event.get('event')=='vehicle_read_state':events.append(event)
    except (ValueError,json.JSONDecodeError):pass
notice('Pleos actual SDK and parking sample',events[-1] if events else 'No CabinVehicle events received')
root=ET.parse(a.output/'screen.xml')
notice('Pleos actual visible text',[node.get('text') for node in root.iter('node') if node.get('text')][:35])
(a.output/'crashes.txt').write_text(run('logcat','-d','-b','crash').stdout)
(a.output/'package.txt').write_text(run('shell','dumpsys','package','io.cabinos.one').stdout)
(a.output/'identity.json').write_text(json.dumps({'fingerprint':identity,'bootSeconds':round(time.monotonic()-start),'submissionReady':False},indent=2))
print('APK installed and actual display/vehicle logs collected. Runtime acceptance still requires review.',flush=True)
