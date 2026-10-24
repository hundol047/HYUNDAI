#!/usr/bin/env python3
"""Install and continuously record the signed APK on a regular Android emulator.
Vehicle signals are explicitly app demo data; this is NOT Pleos/AAOS validation.
"""
import argparse, datetime, hashlib, json, pathlib, re, subprocess, time, xml.etree.ElementTree as ET

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True);p.add_argument('--apk',required=True,type=pathlib.Path)
    p.add_argument('--output',required=True,type=pathlib.Path)
    a=p.parse_args()
    if not re.fullmatch(r'emulator-\d+',a.serial):p.error('Use an explicit emulator serial')
    a.output.mkdir(parents=True,exist_ok=False)
    base=['adb','-s',a.serial]
    def adb(*args,timeout=45,check=True):return subprocess.run(base+list(args),capture_output=True,check=check,timeout=timeout).stdout
    checks=[];started=datetime.datetime.now(datetime.timezone.utc).isoformat();video=None;recorder=None;log_reader=None;log_output=None
    def snapshot(name):
        (a.output/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    def tree():
        adb('shell','uiautomator','dump','/data/local/tmp/cabinos-proof.xml')
        return ET.fromstring(adb('shell','cat','/data/local/tmp/cabinos-proof.xml'))
    def wait(label,seconds=40):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            t=tree()
            # Handle an OS walkthrough if the emulator image restores immersive-mode UI.
            coach=[n for n in t.iter('node') if n.get('text')=='Got it' and n.get('package')=='com.android.systemui']
            if coach:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',coach[0].get('bounds','')))
                adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5);continue
            nodes=[n for n in t.iter('node') if ' '.join(n.get('text','').split())==' '.join(label.split()) or n.get('content-desc')==label]
            if nodes:return nodes[-1]
            time.sleep(.5)
        visible=' | '.join(n.get('text','') or n.get('content-desc','') for n in t.iter('node'))
        raise AssertionError('UI not found: '+label+'; visible: '+visible[:6000])
    def tap(label):
        node=wait(label);x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds','')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.8)
    def check(name,label,image):
        wait(label);snapshot(image);checks.append({'name':name,'result':'passed'});print('PASS',name,flush=True)
    def scenario(label):tap('데모');tap(label);tap('대시보드로 돌아가기')
    try:
        features=adb('shell','pm','list','features').decode()
        if 'android.hardware.type.automotive' in features:raise RuntimeError('Use the dedicated Pleos recorder for automotive devices')
        adb('install','-r',str(a.apk),timeout=120)
        package=adb('shell','dumpsys','package','io.cabinos.one').decode();(a.output/'package.txt').write_text(package)
        installed=a.output/'installed.apk'
        # Package-manager/ADB can briefly disagree immediately after installation.
        # Re-read the path on each bounded retry; the exact-file hash remains mandatory.
        for attempt in range(3):
            path=adb('shell','pm','path','io.cabinos.one').decode().strip().removeprefix('package:')
            try:
                adb('pull',path,str(installed))
                break
            except subprocess.CalledProcessError:
                if attempt==2: raise
                time.sleep(1)

        digest=hashlib.sha256(a.apk.read_bytes()).hexdigest()
        if hashlib.sha256(installed.read_bytes()).hexdigest()!=digest:raise RuntimeError('Installed APK hash mismatch')
        installed.unlink()
        adb('shell','wm','size','1280x800');adb('shell','wm','density','160')
        adb('shell','settings','put','system','accelerometer_rotation','0')
        # Collect while ADB is healthy: a later device disconnect must not erase
        # the startup failure evidence or require another responsive shell.
        log_output=(a.output/'startup-logcat.txt').open('wb')
        log_reader=subprocess.Popen(base+['logcat','-v','threadtime','-T','1','AndroidRuntime:E','ActivityManager:I','*:S'],stdout=log_output,stderr=subprocess.STDOUT)
        adb('shell','am','force-stop','io.cabinos.one')
        adb('shell','am','start','-W','-n','io.cabinos.one/.MainActivity',timeout=90)
        wait('StopSense  +  CabinOS',seconds=90)
        video='/sdcard/cabinos-apk-proof.mp4'
        with (a.output/'screenrecord.log').open('wb') as log:
            recorder=subprocess.Popen(base+['shell','screenrecord','--bit-rate','2500000','--time-limit','180',video],stdout=log,stderr=subprocess.STDOUT)
            time.sleep(2)
            check('APK parked dashboard','StopSense  +  CabinOS','01-parked')
            tap('무엇을 도와드릴까요?');tap('25분 집중 모드 켜줘');tap('닫기')
            check('focus session via assistant command','나의 할 일','02-focus')
            scenario('충전 15분');check('charging estimate changes shared session','나의 할 일','03-charge-update')
            scenario('주행');check('motion locks parked controls','안전한 여정, 함께할게요.','04-driving')
            if any(n.get('text')=='나의 할 일' for n in tree().iter('node')):raise AssertionError('Task editor visible while driving')
            scenario('신호 대기');check('D at zero does not unlock parked screen','신호 대기 중','05-traffic')
            scenario('충전');wait('StopSense  +  CabinOS')
            tap('설정');check('appearance settings accessible','당신에게 맞춘 CabinOS','06-settings')
            tap('웜 샌드');snapshot('07-custom-color')
            tap('홈');wait('StopSense  +  CabinOS');time.sleep(3)
    except Exception as error:
        checks.append({'result':'failed','error':str(error)})
        # Structured CI annotations keep the failure reason visible even when raw log storage is unavailable.
        detail=str(error)+((': '+error.stderr.decode(errors='replace')) if isinstance(error,subprocess.CalledProcessError) and error.stderr else '')
        message=detail.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
        print('::error title=Android APK execution::'+message,flush=True)
        captured=a.output/'startup-logcat.txt'
        if captured.exists():
            early=captured.read_text(errors='replace')[-6000:]
            if early.strip():print('::error title=Captured APK startup::'+early.replace('%','%25').replace('\r','%0D').replace('\n','%0A'),flush=True)
        try:
            crash=adb('logcat','-d','-t','150','-s','AndroidRuntime:E',check=False).decode(errors='replace')
            (a.output/'android-runtime.txt').write_text(crash)
            if crash.strip():print('::error title=Android runtime::'+crash[-6000:].replace('%','%25').replace('\r','%0D').replace('\n','%0A'),flush=True)
            snapshot('failure-screen')
        except Exception as diagnostics_error:
            print('::warning title=Device diagnostics::'+str(diagnostics_error),flush=True)
        raise
    finally:
        if log_reader is not None:
            log_reader.terminate()
            try:log_reader.wait(timeout=5)
            except subprocess.TimeoutExpired:log_reader.kill();log_reader.wait(timeout=5)
        if log_output is not None:log_output.close()
        if recorder is not None:
            if recorder.poll() is None:
                ids=adb('shell','pidof','screenrecord',check=False).decode().split()
                for pid in ids:
                    if pid.isdigit():adb('shell','kill','-2',pid,check=False)
                try:recorder.wait(timeout=20)
                except subprocess.TimeoutExpired:recorder.terminate();recorder.wait(timeout=10)
            adb('pull',video,str(a.output/'CabinOS_ONE_android_apk.mp4'),check=False,timeout=60)
        try:fingerprint=adb('shell','getprop','ro.build.fingerprint',check=False,timeout=10).decode().strip()
        except (subprocess.SubprocessError,OSError):fingerprint='unavailable: device connection lost'
        result={'startedAtUtc':started,'captureMethod':'adb shell screenrecord: continuous device frames',
          'deviceType':'regular Android emulator, NOT Pleos','fingerprint':fingerprint,
          'vehicleSignals':'app simulation, NOT actual vehicle telemetry',
          'apkSha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),'checks':checks,'pleosValidated':False}
        (a.output/'evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    return 0
if __name__=='__main__':raise SystemExit(main())
