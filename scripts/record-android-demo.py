#!/usr/bin/env python3
"""Install and continuously record the signed APK on a regular Android emulator.
Vehicle signals are explicitly app demo data; this is NOT Pleos/AAOS validation.
"""
import argparse, datetime, hashlib, json, pathlib, re, subprocess, time, threading, signal, xml.etree.ElementTree as ET

def host_sample(proc=pathlib.Path('/proc')):
    """Bounded resource metadata only; never read process environments or arguments."""
    def fields(path):
        try:return dict(line.split(':',1) for line in path.read_text().splitlines() if ':' in line)
        except OSError:return {}
    def number(values,key):
        raw=values.get(key,'').strip().split()
        return int(raw[0]) if raw and raw[0].isdigit() else None
    emulators=[]
    for entry in proc.iterdir():
        if not entry.name.isdigit():continue
        try:name=(entry/'comm').read_text().strip()
        except OSError:continue
        if not name.startswith('qemu-system'):continue
        values=fields(entry/'status')
        emulators.append({'pid':int(entry.name),'rssKiB':number(values,'VmRSS'),'threads':number(values,'Threads')})
    return {'monotonic':time.monotonic(),'availableKiB':number(fields(proc/'meminfo'),'MemAvailable'),'emulators':emulators}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True);p.add_argument('--apk',required=True,type=pathlib.Path)
    p.add_argument('--output',required=True,type=pathlib.Path)
    p.add_argument('--package',choices=['io.cabinos.one','io.cabinos.one.preview'],default='io.cabinos.one')
    p.add_argument('--stop-plan',action='store_true',help='Record the parked-plan upgrade scenario')
    p.add_argument('--place-photos',action='store_true',help='Record actual nearby photos using an explicitly simulated Paris GPS position')
    p.add_argument('--navigation-review',action='store_true',help='Record live routing with simulated Seoul GPS and portrait/short-landscape controls')
    p.add_argument('--capture-audio',action='store_true',help='Record real device output with scrcpy, including audio')
    p.add_argument('--audio-review',action='store_true',help='Play all six ambient presets in the installed APK')
    a=p.parse_args()
    if not re.fullmatch(r'emulator-\d+',a.serial):p.error('Use an explicit emulator serial')
    a.output.mkdir(parents=True,exist_ok=False)
    base=['adb','-s',a.serial]
    def adb(*args,timeout=45,check=True):return subprocess.run(base+list(args),capture_output=True,check=check,timeout=timeout).stdout
    resource_stop=threading.Event();resources=[]
    def monitor():
        with (a.output/'host-resources.jsonl').open('w') as out:
            while not resource_stop.is_set():
                try:
                    sample=host_sample();resources.append(sample)
                    out.write(json.dumps(sample)+'\n');out.flush()
                except OSError:pass
                resource_stop.wait(1)
    resource_thread=threading.Thread(target=monitor,daemon=True);resource_thread.start()
    recording_started=None
    audio_windows=[]
    def window(name, action, silent=False):
        start=time.monotonic()-recording_started
        action()
        audio_windows.append(dict(name=name,startSeconds=start,endSeconds=time.monotonic()-recording_started,expectSilence=silent))
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
        wait(label);snapshot(image);checks.append({'name':name,'result':'passed','secondsSinceRecordingStart':round(time.monotonic()-recording_started,2) if recording_started else None});print('PASS',name,flush=True)
    def scenario(label):tap('데모');tap(label);tap('대시보드로 돌아가기')
    try:
        features=adb('shell','pm','list','features').decode()
        if 'android.hardware.type.automotive' in features:raise RuntimeError('Use the dedicated Pleos recorder for automotive devices')
        adb('install','-r',str(a.apk),timeout=120)
        package=adb('shell','dumpsys','package',a.package).decode();(a.output/'package.txt').write_text(package)
        installed=a.output/'installed.apk'
        # Package-manager/ADB can briefly disagree immediately after installation.
        # Re-read the path on each bounded retry; the exact-file hash remains mandatory.
        for attempt in range(3):
            path=adb('shell','pm','path',a.package).decode().strip().removeprefix('package:')
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
        log_reader=subprocess.Popen(base+['logcat','-v','threadtime','-T','1','*:I'],stdout=log_output,stderr=subprocess.STDOUT)
        if a.place_photos or a.navigation_review:
            for permission in ['android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION']:
                adb('shell','pm','grant',a.package,permission)
        if a.capture_audio:
            adb('shell','cmd','media_session','volume','--stream','3','--set','12')
        adb('shell','am','force-stop',a.package)
        adb('shell','am','start','-W','-n',a.package+'/io.cabinos.one.MainActivity',timeout=90)
        wait('StopSense  +  CabinOS',seconds=90)
        adb('shell','dumpsys','gfxinfo',a.package,'reset',check=False)
        (a.output/'memory-before.txt').write_bytes(adb('shell','dumpsys','meminfo',a.package,check=False))
        video='/sdcard/cabinos-apk-proof.mp4'
        recording_started=time.monotonic()
        with (a.output/'screenrecord.log').open('wb') as log:
            capture=(['scrcpy','--serial',a.serial,'--no-playback','--no-window','--no-control','--require-audio','--audio-source=output','--audio-codec=aac','--video-bit-rate=2500000','--record='+str(a.output/'CabinOS_ONE_android_apk.mp4')] if a.capture_audio else base+['shell','screenrecord','--bit-rate','2500000','--time-limit','180',video])
            recorder=subprocess.Popen(capture,stdout=log,stderr=subprocess.STDOUT)
            time.sleep(2)
            if recorder.poll() is not None:
                raise RuntimeError('Recording process stopped before UI scenario; see screenrecord.log')
            if a.navigation_review:
                from record_navigation_review import record
                record(adb, tree, tap, check, snapshot, scenario, audio_window=window if a.capture_audio else None)
            elif a.audio_review:
                from record_audio_review import record
                record(adb, tree, tap, check, snapshot, scenario, window)
            elif a.place_photos:
                from record_place_photos import record
                record(adb, tree, tap, check, snapshot, scenario)
            elif a.stop_plan:
                from record_stop_plan import record
                record(adb, tree, tap, check, snapshot, scenario)
            else:
                check('APK parked dashboard','StopSense  +  CabinOS','01-parked')
                tap('무엇을 도와드릴까요?');tap('25분 집중 모드 켜줘');tap('닫기')
                check('focus session via assistant command','나의 할 일','02-focus')
                if 'versionName=0.11.0' not in package:
                    tap('배경 소리 선택')
                    for index,name in enumerate(['백색 소음','자연의 소리','잔잔한 톤']):
                        tap('▶ '+name)
                        check('ambient preset playback state: '+name,'■ '+name+' 중지','02b-audio-'+str(index))
                    tap('소리 끄기');tap('완료')
                scenario('충전 15분');check('charging estimate changes shared session','나의 할 일','03-charge-update')
                scenario('주행');check('motion locks parked controls','안전한 여정, 함께할게요.','04-driving')
                if 'versionName=0.11.0' not in package:
                    wait('도로 지도 표시 중',seconds=60)
                    check('EchoRoad visible road tiles loaded','판교역 · 데모 위치 (GPS 아님)','04b-real-roads')
                if any(n.get('text')=='나의 할 일' for n in tree().iter('node')):raise AssertionError('Task editor visible while driving')
                scenario('신호 대기');check('D at zero does not unlock parked screen','신호 대기 중','05-traffic')
                scenario('충전');wait('StopSense  +  CabinOS')
                tap('설정');check('appearance settings accessible','당신에게 맞춘 CabinOS','06-settings')
                tap('웜 샌드');snapshot('07-custom-color')
                if 'versionName=0.11.0' not in package:
                    tap('화이트');snapshot('07b-white')
                    tap('블랙');snapshot('07c-black')
                tap('홈');wait('StopSense  +  CabinOS');time.sleep(3)
                if a.package.endswith('.preview'):
                    adb('shell','wm','size','720x1600');adb('shell','wm','density','320')
                    check('phone portrait dashboard','이 시간을, 나답게.','08-phone-portrait')
    except Exception as error:
        checks.append({'result':'failed','error':str(error)})
        # Structured CI annotations keep the failure reason visible even when raw log storage is unavailable.
        detail=str(error)
        if isinstance(error,subprocess.CalledProcessError):
            for label,value in [('stdout',error.stdout),('stderr',error.stderr)]:
                if value:detail+='; '+label+': '+(value.decode(errors='replace') if isinstance(value,bytes) else str(value))[-6000:]
        message=detail.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
        print('::error title=Android APK execution::'+message,flush=True)
        captured=a.output/'startup-logcat.txt'
        if captured.exists():
            early=captured.read_text(errors='replace')[-6000:]
            if early.strip():print('::error title=Captured APK startup::'+early.replace('%','%25').replace('\r','%0D').replace('\n','%0A'),flush=True)
        try:
            crash=adb('logcat','-b','crash','-d',check=False,timeout=10).decode(errors='replace')
            (a.output/'android-runtime.txt').write_text(crash)
            if crash.strip():print('::error title=Android runtime::'+crash[-6000:].replace('%','%25').replace('\r','%0D').replace('\n','%0A'),flush=True)
            snapshot('failure-screen')
        except Exception as diagnostics_error:
            print('::warning title=Device diagnostics::'+str(diagnostics_error),flush=True)
        raise
    finally:
        resource_stop.set();resource_thread.join(timeout=2)
        peak=max((p['rssKiB'] or 0 for sample in resources for p in sample['emulators']),default=0)
        print('Emulator resource samples:',len(resources),'peak RSS KiB:',peak,flush=True)
        if log_reader is not None:
            log_reader.terminate()
            try:log_reader.wait(timeout=5)
            except subprocess.TimeoutExpired:log_reader.kill();log_reader.wait(timeout=5)
        if log_output is not None:log_output.close()
        if recorder is not None:
            if recorder.poll() is None:
                if a.capture_audio:recorder.send_signal(signal.SIGINT)
                ids=[] if a.capture_audio else adb('shell','pidof','screenrecord',check=False).decode().split()
                for pid in ids:
                    if pid.isdigit():adb('shell','kill','-2',pid,check=False)
                try:recorder.wait(timeout=20)
                except subprocess.TimeoutExpired:recorder.terminate();recorder.wait(timeout=10)
            if not a.capture_audio:adb('pull',video,str(a.output/'CabinOS_ONE_android_apk.mp4'),check=False,timeout=60)
        if a.capture_audio:
            (a.output/'audio-windows.json').write_text(json.dumps(audio_windows,ensure_ascii=False,indent=2)+'\n')
        for report,service in [('rendering.txt','gfxinfo'),('memory-after.txt','meminfo')]:
            try:(a.output/report).write_bytes(adb('shell','dumpsys',service,a.package,check=False,timeout=10))
            except (subprocess.SubprocessError,OSError):pass
        try:fingerprint=adb('shell','getprop','ro.build.fingerprint',check=False,timeout=10).decode().strip()
        except (subprocess.SubprocessError,OSError):fingerprint='unavailable: device connection lost'
        result={'applicationId':a.package,'startedAtUtc':started,'captureMethod':('scrcpy: continuous device frames and live Android output audio' if a.capture_audio else 'adb shell screenrecord: continuous device frames'),
          'deviceType':'regular Android emulator, NOT Pleos','fingerprint':fingerprint,
          'vehicleSignals':'app simulation, NOT actual vehicle telemetry',
          'locationSource':('emulator geo fix: Seoul, NOT actual GPS' if a.navigation_review else 'emulator geo fix: Paris, NOT actual GPS' if a.place_photos else 'not tested'),
          'apkSha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),'checks':checks,'pleosValidated':False}
        (a.output/'evidence.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    return 0
if __name__=='__main__':raise SystemExit(main())
