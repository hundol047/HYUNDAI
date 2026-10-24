#!/usr/bin/env python3
"""Record official Console scenarios when active, otherwise a limited actual SDK preview.

Uses the strict recorder unchanged. Its missing D/SOC-change checks remain failures.
This is a limited preview, never a substitute for the required full vehicle recording.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True)
p.add_argument('--apk',required=True,type=pathlib.Path)
p.add_argument('--output',required=True,type=pathlib.Path)
a=p.parse_args()
if not re.fullmatch(r'emulator-\d+',a.serial):p.error('Explicit emulator serial required')
# When the official Console is active, attempt the complete required sequence.
# Preserve its strict result even when UI timing or a real device check fails.
try:
    with urllib.request.urlopen('http://127.0.0.1:8088/vehicle/state',timeout=5) as response:
        console_active=json.load(response).get('controls',{}).get('emulator_active') is True
except (OSError,ValueError):
    console_active=False
if console_active:
    code=subprocess.call([sys.executable,str(pathlib.Path(__file__).with_name('exercise-pleos-device.py')),
        '--serial',a.serial,'--apk',str(a.apk),'--output',str(a.output)])
    evidence=json.loads((a.output/'take/evidence.json').read_text())
    summary={'captureMethod':evidence['captureMethod'],'apkSha256':evidence['apkSha256'],
        'mode':'console_scenarios','recordingSecondsRequested':120,'fullRecorderExitCode':code,
        'fullScenarioChecks':evidence['checks'],'submissionReady':False,'visualReview':'pending',
        'limitations':['Check every fullScenarioChecks value and review the continuous video before claiming scenario acceptance',
                       'Command buttons are touch actions, not microphone/STT demonstration']}
    (a.output/'preview.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    message=json.dumps(summary,ensure_ascii=False).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
    print('::notice title=Actual Pleos Console scenario recording::'+message,flush=True)
    if not evidence['checks'].get('officialSdkReadObserved'):
        raise SystemExit('No real SDK read in the recording')
    raise SystemExit(0)

a.output.mkdir(parents=True,exist_ok=False)
def adb(*args,timeout=30):
    return subprocess.run(['adb','-s',a.serial,*args],capture_output=True,check=True,timeout=timeout).stdout
fingerprint=adb('shell','getprop','ro.build.fingerprint').decode().strip()
if not fingerprint.startswith('Pleos/') or 'release_connect_v2.1.0' not in fingerprint:
    raise SystemExit('Official Pleos 2.1.0 image required')
start=time.monotonic()
actions=[]
def note(text):
    actions.append({'seconds':round(time.monotonic()-start,2),'action':text})
    print(text,flush=True)
def tree():
    adb('shell','uiautomator','dump','/sdcard/cabinos-preview.xml',timeout=40)
    return ET.fromstring(adb('shell','cat','/sdcard/cabinos-preview.xml'))
def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds',''))))
def tap_node(node):
    box=bounds(node)
    if len(box)!=4 or box[2]<=box[0] or box[3]<=box[1]:raise RuntimeError('Control has no visible bounds')
    adb('shell','input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2))
def tap(label):
    for _ in range(3):
        nodes=[n for n in tree().iter('node') if n.get('text')==label or n.get('content-desc')==label]
        if nodes:tap_node(nodes[-1]);note('Actual UI: '+label);return
        time.sleep(1)
    raise RuntimeError('Actual UI control unavailable: '+label)
record=subprocess.Popen([sys.executable,str(pathlib.Path(__file__).with_name('record-vehicle-emulator.py')),
    '--serial',a.serial,'--apk',str(a.apk),'--seconds','110','--output',str(a.output/'take')])
ui_error=None
try:
    time.sleep(8)
    tap('차량 상태');time.sleep(8);tap('대시보드로 돌아가기')
    tap('시간 설정')
    tap('45분')
    tap('적용');tap('음성');tap('25분 집중 모드 켜줘');tap('닫기')
    time.sleep(12)
    tap('홈')
    for _ in range(4):
        root=tree()
        headings=[n for n in root.iter('node') if n.get('text')=='충전 후 출발 계획']
        heading_y=min((bounds(n)[1] for n in headings if len(bounds(n))==4),default=10**9)
        chips=[n for n in root.iter('node') if n.get('text')=='100%' and n.get('package')=='io.cabinos.one'
               and len(bounds(n))==4 and bounds(n)[1]>heading_y]
        if chips:
            tap_node(chips[-1]);note('Actual UI: select SOC planning target 100%')
            time.sleep(2)
            pid=adb('shell','pidof','io.cabinos.one').decode().strip()
            observed=adb('logcat','--pid='+pid,'-d','-v','raw','-t','20','CabinVehicle:I','*:S').decode(errors='replace')
            actual=[]
            for line in observed.splitlines():
                try:actual.append(json.loads(line))
                except ValueError:pass
            if any(e.get('chargeTargetPercent')==100 and e.get('sdkSocUsed') and e.get('planSource')=='Pleos SOC 목표 도달' for e in actual):break
            continue
        app_nodes=[n for n in root.iter('node') if n.get('package')=='io.cabinos.one' and len(bounds(n))==4]
        if not app_nodes:raise RuntimeError('Actual app window unavailable')
        b=max((bounds(n) for n in app_nodes),key=lambda b:(b[2]-b[0])*(b[3]-b[1]))
        adb('shell','input','swipe',str((b[0]+b[2])//2),str(round(b[3]*.8)),str((b[0]+b[2])//2),str(round(b[3]*.4)),'400')
    else:raise RuntimeError('SDK goal was not observed after actual UI taps; no alternative value injected')
    time.sleep(8)
    tap('차량 상태');time.sleep(8);tap('대시보드로 돌아가기')
except Exception as error:
    ui_error=type(error).__name__+': '+str(error)
    note(ui_error)
finally:
    result=record.wait(timeout=170)
    (a.output/'actions.json').write_text(json.dumps(actions,ensure_ascii=False,indent=2)+'\n')
evidence_path=a.output/'take/evidence.json'
if not evidence_path.exists() or not (a.output/'take/device-screen.mp4').is_file():
    raise SystemExit('No continuous recording with APK identity evidence was produced')
evidence=json.loads(evidence_path.read_text())
events=evidence['events']
summary={'mode':'initial_sdk_preview','captureMethod':evidence['captureMethod'],'apkSha256':evidence['apkSha256'],
    'fingerprint':fingerprint,'recordingSecondsRequested':110,'uiError':ui_error,
    'sdkSocReadObserved':any(e.get('sdkBatteryPercent') is not None and e.get('sdkSocUsed') for e in events),
    'sdkGoalCompletionObserved':any(e.get('sdkSocUsed') and e.get('chargeTargetPercent')==100 and e.get('availableSeconds')==0 and e.get('sessionStatus')=='COMPLETED' for e in events),
    'fullRecorderExitCode':result,'fullScenarioChecks':evidence['checks'],'submissionReady':False,
    'limitations':['Console returned emulator_inactive; no successful P/D or SOC-change injection is claimed',
                   'Initial SDK SOC is read from the official emulator; setting a planning goal does not change vehicle SOC',
                   'Voice command button is a touch action, not a microphone/STT demonstration'],
    'visualReview':'pending'}
(a.output/'preview.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
message=json.dumps(summary,ensure_ascii=False).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
print('::notice title=Limited actual Pleos SDK recording::'+message,flush=True)
if not summary['sdkSocReadObserved']:raise SystemExit('No real SDK SOC observation; do not publish as SDK proof')
