"""Real APK UI + live place/photo services. Paris GPS and vehicle state are simulated."""
import time
import threading
import re


def category_control(tree, label):
    # A loading photo also contains the category text. The filter is the topmost
    # visible match, not the last matching placeholder inside a place card.
    matches=[]
    for node in tree.iter('node'):
        bounds=list(map(int,re.findall(r'-?\d+',node.get('bounds',''))))
        if node.get('text')==label and len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1]:
            matches.append((bounds,node))
    if not matches:
        raise AssertionError('Category control missing: '+label)
    return min(matches,key=lambda item:item[0][1])[0]


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

    def category(label):
        x1,y1,x2,y2=category_control(tree(),label)
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            for node in tree().iter('node'):
                value=node.get('text','')
                if re.match(re.escape(label)+r" \d+곳 ·",value):
                    check('category filter selected: '+label,value,'filter-'+label)
                    return
            time.sleep(1)
        raise AssertionError('Category did not change: '+label)

    def reveal_photo(name, fragment, image):
        for _ in range(4):
            adb('shell', 'input', 'swipe', '1120', '455', '1120', '595', '350')
        for _ in range(6):
            nodes = list(tree().iter('node'))
            if any(fragment in n.get('content-desc', '') for n in nodes):
                expect(name, fragment, image)
                return
            # Only scroll inside the visible result list (y=435..617), not the footer.
            if any(fragment.removesuffix(' 등록 사진') in n.get('text', '') for n in nodes):
                expect(name, fragment, image)
                return
            adb('shell', 'input', 'swipe', '1120', '585', '1120', '455', '600')
            time.sleep(3)
        raise AssertionError('Place not reached by list scrolling: ' + fragment)

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
        category('카페')
        expect('live cafe list from simulated Paris GPS', 'Café de Flore', 'photos-01-cafes')
        expect('Flore actual photo loaded', 'Café de Flore 등록 사진', 'photos-02-flore')
        # Keep the nearest results visible; both cafes are within 30 metres.
        time.sleep(2)
        reveal_photo('second cafe actual photo loaded', 'Les Deux Magots 등록 사진', 'photos-03-deux-magots')
        time.sleep(1)
        category('산책')
        adb('shell', 'input', 'swipe', '1120', '455', '1120', '595', '450')
        reveal_photo('park actual photo loaded', 'Square Laurent Prache 등록 사진', 'photos-04-walking')
        time.sleep(1)
        category('쇼핑')
        time.sleep(2)
        snapshot('photos-05-shopping')
        category('카페')
        time.sleep(1)
        snapshot('photos-06-return')
    finally:
        stop.set()
        sender.join(timeout=5)
