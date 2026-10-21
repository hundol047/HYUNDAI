#!/usr/bin/env python3
"""Actual-device UI smoke checks. Requires one adb device and the installed app.
Does not clear app data; scenarios modify demo sessions/preferences. Result is truthful on failure.
"""
from pathlib import Path
import subprocess, os, re, time, json, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
ADB = str(Path(os.environ.get('ANDROID_HOME', '/workspace/toolchains/android-sdk'))/'platform-tools/adb')
OUT = ROOT/'artifacts/device-smoke'
OUT.mkdir(parents=True, exist_ok=True)
checks=[]

def adb(*args, timeout=40):
    return subprocess.run([ADB,*args],capture_output=True,check=True,timeout=timeout).stdout

def tree():
    result=adb('shell','uiautomator','dump','/data/local/tmp/cabinos-ui.xml',timeout=60)
    if b'dumped to:' not in result:
        raise RuntimeError('UIAutomator dump did not complete: '+result.decode(errors='replace')[:500])
    raw=adb('shell','cat','/data/local/tmp/cabinos-ui.xml')
    return ET.fromstring(raw)

def labels(t): return '\n'.join(n.get('text','')+' '+n.get('content-desc','') for n in t.iter('node'))

def wait_text(text, timeout=90):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        t=tree()
        if text in labels(t): return t
        time.sleep(1)
    raise AssertionError('UI text not observed: '+text)

def tap(text):
    t=wait_text(text)
    nodes=[n for n in t.iter('node') if n.get('text')==text or n.get('content-desc')==text]
    if not nodes: raise AssertionError('Exact label not found: '+text)
    n=nodes[-1]
    points=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    assert len(points)==4,(text,n.attrib)
    x1,y1,x2,y2=points
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.6)

def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',timeout=60))
    t=tree();(OUT/(name+'.xml')).write_bytes(ET.tostring(t,encoding='utf-8'))

def check(name,fn):
    fn(); checks.append({'name':name,'result':'passed'});print('PASS',name,flush=True)

def scenario(name):
    tap('데모');tap(name);tap('대시보드로 돌아가기')

def launch():
    adb('shell','am','start','-n','io.cabinos.one/.MainActivity',timeout=90)
    wait_text('CabinOS')

def parked():
    scenario('충전');wait_text('이 시간을, 나답게.');wait_text('StopSense  +  CabinOS');screenshot('01-parked')

def focus():
    # Start via the same assistant/domain command used by voice, avoiding ambiguous repeated "시작하기".
    tap('무엇을 도와드릴까요?');tap('25분 집중 모드 켜줘');tap('닫기')
    wait_text('나의 할 일');screenshot('02-focus')

def charging_update():
    scenario('충전 15분');wait_text('나의 할 일');t=tree()
    assert re.search(r'0[89]:\d\d',labels(t)),labels(t)
    screenshot('03-shortened-session')

def motion():
    scenario('주행');wait_text('안전한 여정, 함께할게요.')
    t=tree();assert '나의 할 일' not in labels(t);assert '시작하기' not in labels(t)
    screenshot('04-driving')

def traffic():
    scenario('신호 대기');wait_text('신호 대기 중');t=tree()
    assert '시작하기' not in labels(t);assert 'StopSense  +  CabinOS' not in labels(t)
    screenshot('05-traffic')

def disconnected():
    scenario('SDK 끊김');wait_text('안전한 연결을 기다립니다.');screenshot('06-disconnected')

def restore():
    scenario('충전');wait_text('이 시간을, 나답게.')
    adb('shell','am','force-stop','io.cabinos.one');launch();wait_text('이 시간을, 나답게.');wait_text('일시정지됨');screenshot('07-restored-paused')

try:
    launch()
    check('confirmed parking activates unified StopSense + CabinOS',parked)
    check('Korean command starts focus and exposes task controls',focus)
    check('charging update truncates shared session countdown',charging_update)
    check('motion automatically replaces parked dashboard with EchoRoad',motion)
    check('traffic stop keeps parked features restricted',traffic)
    check('SDK loss fails closed',disconnected)
    check('process restart restores a paused session',restore)
except Exception as exc:
    checks.append({'result':'failed','error':str(exc)});raise
finally:
    (OUT/'results.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
