"""Exercise the installed app's spoken-preview button using its actual Android TTS engine."""
import time


def record(adb, tree, tap, check, snapshot, scenario, window, language):
    tap('내비')
    tap('언어 · ko-KR')
    import uiautomator2
    device = uiautomator2.connect(adb('get-serialno').decode().strip())
    try:
        field = device(className='android.widget.EditText')
        field.set_text(language)
        if field.get_text() != language:
            raise AssertionError('Voice language was not entered')
    finally:
        device.stop_uiautomator()
    tap('적용')
    check('requested navigation language selected', '언어 · '+language, 'speech-language')
    tap('설정')
    for _ in range(7):
        if any(n.get('text') == '안내 음성 들어보기' for n in tree().iter('node')):
            break
        adb('shell', 'input', 'swipe', '1100', '650', '1100', '280', '600')
        time.sleep(1)
    check('voice preview and settings reachable', '안내 음성 들어보기', 'speech-settings')
    def listen():
        tap('안내 음성 들어보기')
        # Include neural engine synthesis latency and the complete utterance.
        time.sleep(20)
    window(language+' actual app spoken preview', listen)
    snapshot('speech-after-playback')
    if any('미지원/미설치' in n.get('text','') or '오프라인 음성팩을 설치' in n.get('text','') for n in tree().iter('node')):
        raise AssertionError('Requested offline voice was unavailable')
    check('preview completed without leaving settings', '안내 음성 들어보기', 'speech-finished')
