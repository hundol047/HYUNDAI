"""Real APK UI + live place/photo services. Paris GPS and vehicle state are simulated."""
import time
import threading


def record(adb, tree, tap, check, snapshot, scenario):
    def expect(name, fragment, image, seconds=40):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            for node in tree().iter('node'):
                for attr in ('text', 'content-desc'):
                    value = node.get(attr, '')
                    if fragment in value:
                        check(name, value, image)
                        return
            time.sleep(1)
        raise AssertionError('Expected loaded place photo/control: ' + fragment)

    stop = threading.Event()
    def location():
        while not stop.is_set():
            adb('emu', 'geo', 'fix', '2.3328', '48.8541')
            stop.wait(2)
    sender = threading.Thread(target=location, daemon=True)
    sender.start()
    try:
        tap('지도')
        # Automatic one-shot location uses the emulator fix; do not seed app databases.
        deadline = time.monotonic() + 65
        while time.monotonic() < deadline:
            nodes = list(tree().iter('node'))
            if any('Café de Flore' in n.get('text', '') for n in nodes):
                break
            time.sleep(2)
        tap('카페')
        expect('live cafe list from simulated Paris GPS', 'Café de Flore', 'photos-01-cafes')
        expect('Flore actual photo loaded', 'Café de Flore 등록 사진', 'photos-02-flore')
        # Keep the nearest results visible; both cafes are within 30 metres.
        time.sleep(6)
        expect('second cafe actual photo loaded', 'Les Deux Magots 등록 사진', 'photos-03-deux-magots')
        time.sleep(4)
        tap('산책')
        adb('shell', 'input', 'swipe', '1120', '410', '1120', '660', '450')
        expect('park actual photo loaded', 'Square Laurent Prache 등록 사진', 'photos-04-walking')
        time.sleep(4)
        tap('쇼핑')
        time.sleep(10)
        snapshot('photos-05-shopping')
        tap('카페')
        time.sleep(4)
        snapshot('photos-06-return')
    finally:
        stop.set()
        sender.join(timeout=5)
