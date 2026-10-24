#!/usr/bin/env python3
"""Collect actual official-emulator diagnostics; never marks a submission complete."""
import argparse
import hashlib
import json
import os
import shutil
import sys
import re
import xml.etree.ElementTree as ET
import subprocess
import time
import urllib.request
import urllib.error
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
    detail=kind.__name__+': '+str(value)
    if isinstance(value,urllib.error.HTTPError):detail+=' at '+value.url
    message=detail.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
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
console_initialized=False
while time.monotonic()-start < 600:
    try:
        boot=run('shell','getprop','sys.boot_completed',timeout=10,check=False)
        if 'unauthorized' in boot.stderr and time.monotonic()-start > 30:
            raise RuntimeError('ADB host is unauthorized. Start the ADB server before the emulator; do not disable authentication.')
        if boot.stdout.strip() == '1':
            break
    except subprocess.TimeoutExpired:
        pass
    # The official Console can become ready before Android boot completes.
    # Supply the requested P/0 initial scenario through its public UI endpoints.
    if not console_initialized:
        try:
            fingerprint=run('shell','getprop','ro.build.fingerprint',timeout=8,check=False).stdout.strip()
            if fingerprint.startswith('Pleos/') and 'release_connect_v2.1.0' in fingerprint:
                run('forward','tcp:8088','tcp:8088',timeout=8)
                for endpoint in ['/vehicle/controls?speed=0&accelerator=0&brake=0','/vehicle/gear?gear=Park']:
                    with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8088'+endpoint,method='PUT'),timeout=5) as response:
                        response.read()
                console_initialized=True
                notice('Official Console initialized','P / 0 submitted to official emulator before app launch')
        except (OSError,subprocess.TimeoutExpired,subprocess.CalledProcessError):
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
def console_request(endpoint, method='GET'):
    for attempt in range(12):
        try:
            with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8088'+endpoint,method=method),timeout=5) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code not in (500,502,503,504) or attempt==11:raise
        except OSError:
            if attempt==11:raise
        time.sleep(2)

for endpoint in ['/', '/vehicle/catalog', '/vehicle/state']:
    try:
        data=console_request(endpoint)
        (a.output / ('console'+endpoint.replace('/','-')+('.html' if endpoint=='/' else '.json'))).write_bytes(data)
    except OSError as error:
        notice('Console diagnostic read failed',endpoint+': '+str(error))
# Public Console inputs target this verified emulator, not the app simulator.
scenario_ready=True
for endpoint in ['/vehicle/controls?speed=0&accelerator=0&brake=0','/vehicle/gear?gear=Park',
                 '/vehicle/ev-battery?field=soc&value=68','/vehicle/ev-battery?field=charge_switch&value=true']:
    try: console_request(endpoint,'PUT')
    except OSError as error:
        scenario_ready=False
        notice('Console scenario input failed',endpoint+': '+str(error))
run('install','-r','-g',str(a.apk),timeout=120)
installed=run('shell','pm','path','io.cabinos.one').stdout.strip().splitlines()
base=next((line.removeprefix('package:') for line in installed if line.endswith('/base.apk')),None)
if not base:raise RuntimeError('Installed base APK was not found')
installed_apk=a.output/'installed-release.apk'
run('pull',base,str(installed_apk),timeout=120)
apk_sha=hashlib.sha256(a.apk.read_bytes()).hexdigest()
if hashlib.sha256(installed_apk.read_bytes()).hexdigest()!=apk_sha:raise RuntimeError('Installed APK hash mismatch')
installed_apk.unlink()
notice('Installed APK SHA-256',apk_sha)
run('shell','setprop','log.tag.CabinVehicle','INFO')
run('shell','am','start','-W','-n','io.cabinos.one/.MainActivity',timeout=90)
time.sleep(8)
app_pid=run('shell','pidof','io.cabinos.one').stdout.strip()
if not app_pid:raise RuntimeError('APK process is not running after launch')
run('shell','uiautomator','dump','/sdcard/cabinos-inspect.xml',timeout=45)
run('pull','/sdcard/cabinos-inspect.xml',str(a.output/'screen.xml'))
with (a.output/'screen.png').open('wb') as f:
    subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
(a.output/'vehicle-logcat.txt').write_text(run('logcat','-d','-v','threadtime','CabinVehicle:I','*:S').stdout)
events=[]
for line in (a.output/'vehicle-logcat.txt').read_text().splitlines():
    columns=line.split()
    if len(columns)<3 or columns[2]!=app_pid:continue
    try:
        event=json.loads(line[line.index('{'):])
        if event.get('event')=='vehicle_read_state':events.append(event)
    except (ValueError,json.JSONDecodeError):pass
notice('Pleos actual SDK and parking sample',events[-1] if events else 'No CabinVehicle events received')
root=ET.parse(a.output/'screen.xml')
notice('Pleos actual visible text',[node.get('text') for node in root.iter('node') if node.get('text')][:35])
# Read the actual diagnostic panel even when missing signals keep the dashboard locked.
nodes=[node for node in root.iter('node') if node.get('text')=='차량 상태']
if nodes:
    bounds=list(map(int,re.findall(r'\d+',nodes[-1].get('bounds',''))))
    if len(bounds)==4:
        run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
        time.sleep(2)
        run('shell','uiautomator','dump','/sdcard/cabinos-status.xml',timeout=45)
        run('pull','/sdcard/cabinos-status.xml',str(a.output/'vehicle-status.xml'))
        status=ET.parse(a.output/'vehicle-status.xml')
        notice('Pleos vehicle diagnostics text',[node.get('text') for node in status.iter('node') if node.get('text')][:45])
(a.output/'crashes.txt').write_text(run('logcat','-d','-b','crash').stdout)
package=run('shell','dumpsys','package','io.cabinos.one').stdout
(a.output/'package.txt').write_text(package)
notice('Actual vehicle permission grants','\n'.join(line.strip() for line in package.splitlines() if 'pleos.car.permission' in line or 'android.car.permission' in line))
packages=run('shell','pm','list','packages','-f').stdout
related=[line for line in packages.splitlines() if any(word in line.lower() for word in ['pleos','vehicle','navi','sdv'])]
notice('Installed Pleos services',related[:40])
log=run('logcat','-d','-v','threadtime','-t','3000').stdout
(a.output/'runtime-logcat.txt').write_text(log)
app_errors=[];native_errors=[]
for line in log.splitlines():
    columns=line.split()
    if len(columns)>4 and columns[2]==app_pid and columns[4] in ('W','E','F') and 'CabinVehicle:' not in line:
        app_errors.append(line[:500])
    if any(word in line.lower() for word in ['panicked at','app_framework','dds unavailable','failed to initialize','failed to connect','connect error']):
        native_errors.append(line[:500])
notice('Actual SDK app log', '\n'.join(app_errors[-12:]))
notice('Actual native service errors', '\n'.join(native_errors[-8:]))
boot_diagnostics()
for endpoint in ['/vehicle/gear?gear=Park','/vehicle/ev-battery?field=soc&value=68']:
    try: console_request(endpoint,'PUT')
    except urllib.error.HTTPError as error:
        notice('Console error response', endpoint+': '+error.read(1500).decode(errors='replace'))
(a.output/'identity.json').write_text(json.dumps({'fingerprint':identity,'diagnosticSeconds':round(time.monotonic()-start),'apkSha256':apk_sha,'installedApkHashMatched':True,'consoleScenarioReady':scenario_ready,'submissionReady':False},indent=2))
print('APK installed and actual display/vehicle logs collected. Runtime acceptance still requires review.',flush=True)
