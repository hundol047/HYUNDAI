"""Installed APK navigation usability checks: live OSRM/tiles, explicitly simulated GPS.
No injected routes, app database writes, physical GPS or Pleos validation.
"""
import threading
import time


def record(adb, tree, tap, check, snapshot, scenario, audio_window=None):
    stop = threading.Event()
    errors = []

    def gps():
        try:
            while not stop.is_set():
                adb('emu', 'geo', 'fix', '126.972512', '37.554704')
                stop.wait(1)
        except Exception as error:
            errors.append(str(error))

    def texts():
        return [n.get('text', '') for n in tree().iter('node')]

    sender = threading.Thread(target=gps, daemon=True)
    sender.start()
    try:
        tap('내비')
        tap('내 위치 사용')
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if any('확인 · 정확도 약' in text for text in texts()):
                break
            time.sleep(1)
        else:
            raise AssertionError('Emulator GPS did not become available')
        tap('가게명·지점·도시·주소 또는 위도,경도')
        # IME startup/resizing on software GPU is asynchronous. Wait for the
        # focused input bounds to settle before injecting the user's keystrokes.
        previous = None
        for _ in range(12):
            fields=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText' and n.get('focused')=='true']
            bounds=fields[0].get('bounds') if fields else None
            if bounds and bounds==previous:
                break
            previous=bounds
            time.sleep(1)
        else:
            raise AssertionError('Destination input did not settle after opening the keyboard')
        # Use Android accessibility text entry, as in UI instrumentation tests.
        # This verifies app search and routing, not physical keyboard keystrokes.
        import uiautomator2
        device=uiautomator2.connect(adb('get-serialno').decode().strip())
        try:
            field=device(className='android.widget.EditText')
            field.set_text('37.567005,126.977893')
            if '37.567005,126.977893' not in field.get_text():
                raise AssertionError('Accessibility destination entry was not retained')
        finally:
            device.stop_uiautomator()
        check('accessibility destination entry remains visible with landscape keyboard', '37.567005,126.977893', 'nav-00-keyboard')
        adb('shell','input','keyevent','4')
        tap('검색')
        tap('1. 좌표 목적지 37.567005, 126.977893')
        check('live OSRM route automatically expands installed APK map', '지도 축소', 'nav-01-expanded')
        check('stop control available during guidance', '안내 종료', 'nav-02-controls')
        # Move only inside the left map pane, not the right guidance controls.
        adb('shell', 'input', 'swipe', '300', '350', '470', '430', '700')
        check('manual pan suspends map follow and exposes recenter', '내 위치로', 'nav-03-pan')
        tap('내 위치로')
        if '내 위치로' in texts():
            raise AssertionError('Recenter failed to restore following')
        check('recenter preserves active route', '안내 종료', 'nav-04-recenter')
        if audio_window is not None:
            def listen():
                tap('다시 듣기')
                time.sleep(20)
            audio_window('Korean navigation repeat from app TTS', listen)
            check('navigation repeat recorded with device audio', '안내 종료', 'nav-audio-repeat')
            tap('안내 종료')
            tap('홈')
            check('audio navigation returns home', '이 시간을, 나답게.', 'nav-audio-home')
            return
        adb('shell', 'wm', 'size', '1024x480')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
        time.sleep(3)
        check('large-font short landscape preserves expanded map', '지도 축소', 'nav-05-short-large-font')
        check('short landscape stop remains reachable', '안내 종료', 'nav-06-short-controls')
        # Keep the same 360x800 dp portrait layout inside screenrecord's initial
        # 1280x800 pixel canvas, so the continuous video does not crop its bottom.
        # Portrait recreates the activity; it must retain navigation and the dialog.
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        adb('shell', 'wm', 'size', '360x800')
        adb('shell', 'wm', 'density', '160')
        time.sleep(3)
        check('portrait rotation retains expanded guidance', '지도 축소', 'nav-07-portrait')
        tap('안내 종료')
        time.sleep(3)
        tap('홈')
        check('stopped navigation returns to parked dashboard', '이 시간을, 나답게.', 'nav-08-finish')
        if errors:
            raise AssertionError('GPS sender failed: '+errors[0])
    finally:
        stop.set()
        sender.join(timeout=5)
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0', check=False)
