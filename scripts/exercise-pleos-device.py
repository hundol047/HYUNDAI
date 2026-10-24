#!/usr/bin/env python3
"""Drive the official Virtual Console and actual APK UI during continuous ADB recording.

No screenshots are assembled into video; no app simulator or app-private API is used.
The recorder validates the installed APK hash and actual SDK/vehicle event evidence.
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


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True)
    p.add_argument('--apk',required=True,type=pathlib.Path)
    p.add_argument('--output',required=True,type=pathlib.Path)
    a=p.parse_args()
    if not re.fullmatch(r'emulator-\d+',a.serial):p.error('An emulator serial is required')
    a.output.mkdir(parents=True,exist_ok=False)
    base=['adb','-s',a.serial]
    def adb(*args,timeout=30):
        return subprocess.run(base+list(args),capture_output=True,check=True,timeout=timeout).stdout
    identity=adb('shell','getprop','ro.build.fingerprint').decode()
    if not identity.startswith('Pleos/') or 'release_connect_v2.1.0' not in identity:
        raise RuntimeError('Refusing to exercise an unverified emulator image')
    adb('forward','tcp:8088','tcp:8088')
    steps=[]
    started=time.monotonic()
    def note(action):
        steps.append({'elapsedSeconds':round(time.monotonic()-started,2),'action':action})
        print(action,flush=True)
    def console(endpoint):
        with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8088'+endpoint,method='PUT'),timeout=10) as r:
            if r.status!=200:raise RuntimeError('Official Console rejected '+endpoint)
            r.read()
        note('Official Console: '+endpoint)
    def tree():
        adb('shell','uiautomator','dump','/sdcard/cabinos-record.xml')
        return ET.fromstring(adb('shell','cat','/sdcard/cabinos-record.xml'))
    def bounds(node):
        return list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    def wait(label,seconds=20):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            root=tree()
            nodes=[n for n in root.iter('node') if n.get('text')==label or n.get('content-desc')==label]
            if nodes:return nodes[-1]
            time.sleep(.5)
        visible=[n.get('text') for n in root.iter('node') if n.get('text')]
        raise RuntimeError('Actual UI missing '+label+': '+json.dumps(visible,ensure_ascii=False)[:1500])
    def tap(label):
        box=bounds(wait(label));adb('shell','input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2));note('APK UI: '+label)
    def snapshot(name):
        (a.output/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    def scroll_to(label):
        for _ in range(5):
            root=tree()
            if any(n.get('text')==label for n in root.iter('node')):return
            app_nodes=[n for n in root.iter('node') if n.get('package')=='io.cabinos.one' and len(bounds(n))==4]
            if not app_nodes:raise RuntimeError('Actual app window unavailable')
            box=max((bounds(n) for n in app_nodes),key=lambda b:(b[2]-b[0])*(b[3]-b[1]))
            x=(box[0]+box[2])//2
            adb('shell','input','swipe',str(x),str(int(box[3]*.8)),str(x),str(int(box[3]*.35)),'400')
        raise RuntimeError('Planning control is not reachable: '+label)

    console('/vehicle/controls?speed=0&accelerator=0&brake=0')
    console('/vehicle/gear?gear=Park')
    console('/vehicle/ev-battery?field=soc&value=68')
    console('/vehicle/ev-battery?field=charge_switch&value=true')
    take=a.output/'take'
    record=subprocess.Popen([sys.executable,str(pathlib.Path(__file__).with_name('record-vehicle-emulator.py')),
        '--serial',a.serial,'--apk',str(a.apk),'--seconds','120','--output',str(take)])
    try:
        time.sleep(6)
        wait('이 시간을, 나답게.');snapshot('01-parked')
        console('/vehicle/gear?gear=Drive');time.sleep(6);snapshot('02-drive-zero')
        console('/vehicle/controls?speed=35&accelerator=0&brake=0');time.sleep(6);snapshot('03-moving')
        console('/vehicle/controls?speed=0&accelerator=0&brake=0')
        console('/vehicle/gear?gear=Park');time.sleep(2)
        adb('shell','am','start','-W','-n','io.cabinos.one/.MainActivity')
        wait('이 시간을, 나답게.')
        tap('시간 설정')
        tap('45분')
        tap('적용')
        scroll_to('80%');tap('80%')
        tap('음성');tap('25분 집중 모드 켜줘');tap('닫기')
        time.sleep(4);snapshot('04-focus')
        console('/vehicle/ev-battery?field=soc&value=75');time.sleep(4)
        console('/vehicle/ev-battery?field=soc&value=80');time.sleep(5);snapshot('05-soc-target')
        console('/vehicle/ev-battery?field=charge_switch&value=false');time.sleep(4)
        adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(2)
        adb('shell','am','start','-W','-n','io.cabinos.one/.MainActivity');time.sleep(4)
        snapshot('06-resume')
        result=record.wait(timeout=150)
        if result:raise RuntimeError('Continuous recorder evidence did not pass; inspect actual logs/video')
    except Exception as error:
        text=(type(error).__name__+': '+str(error)).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
        print('::error title=Pleos continuous APK exercise::'+text[:6000],flush=True)
        try:snapshot('failure-screen')
        except Exception:pass
        raise
    finally:
        (a.output/'actions.json').write_text(json.dumps(steps,ensure_ascii=False,indent=2)+'\n')
        if record.poll() is None:
            # Let the bounded device recording finish so failure evidence stays continuous.
            try:record.wait(timeout=150)
            except subprocess.TimeoutExpired:record.terminate();record.wait(timeout=10)
    return 0


if __name__=='__main__':
    raise SystemExit(main())
