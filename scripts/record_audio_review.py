"""Record real APK ambient playback; no replacement soundtrack or synthesized narration."""
import time


def record(adb, tree, tap, check, snapshot, scenario, window):
    tap('무엇을 도와드릴까요?')
    tap('25분 집중 모드 켜줘')
    tap('닫기')
    check('focus session active', '나의 할 일', 'audio-focus')
    tap('배경 소리 선택')
    for index, name in enumerate(['백색 소음', '자연의 소리', '잔잔한 톤', '빗소리', '계곡 소리', '파도 소리']):
        label = '▶ ' + name
        for _ in range(6):
            nodes = list(tree().iter('node'))
            if any(n.get('text') == label for n in nodes):
                break
            adb('shell', 'input', 'swipe', '640', '510', '640', '310', '650')
        tap(label)
        check('audible preset selected: ' + name, '■ ' + name + ' 중지', 'audio-' + str(index))
        time.sleep(2)
        window(name, lambda: time.sleep(9))
    tap('소리 끄기')
    time.sleep(2)
    window('stopped silence', lambda: time.sleep(4), silent=True)
    tap('완료')
    check('sound stopped and focus session retained', '배경 소리 선택', 'audio-stopped')
