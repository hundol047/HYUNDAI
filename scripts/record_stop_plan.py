"""UI-only walkthrough of the installed APK; all vehicle inputs are app demo scenarios."""
import re
import time


def record(adb, tree, tap, check, snapshot, scenario):
    def find(label):
        return next((n for n in tree().iter('node') if n.get('text') == label), None)

    def reveal(label):
        for _ in range(5):
            if find(label) is not None:
                return
            adb('shell', 'input', 'swipe', '1120', '650', '1120', '230', '500')
            time.sleep(1)
        raise AssertionError('Scrolled control not found: ' + label)

    def top():
        for _ in range(3):
            adb('shell', 'input', 'swipe', '1120', '250', '1120', '680', '350')
        time.sleep(1)

    def remaining():
        for node in tree().iter('node'):
            match = re.fullmatch(r'집중 (\d+):(\d+) 남음 · 진행 중', node.get('text', ''))
            if match:
                return int(match[1]) * 60 + int(match[2])
        raise AssertionError('Active focus countdown missing')

    check('upgrade plan visible', '나의 정차 플랜', 'plan-01-dashboard')
    scenario('주행')
    check('motion locks cabin', '안전한 여정, 함께할게요.', 'plan-02-drive-lock')
    scenario('주차')
    top()
    reveal('30분 뒤 출발')
    check('manual departure presets', '30분 뒤 출발', 'plan-03-presets')
    tap('30분 뒤 출발')
    # Give the plan extra room so extension has a nonzero budget.
    top(); tap('시간 설정'); tap('45분'); tap('적용')
    reveal('25분 집중 바로 시작')
    tap('25분 집중 바로 시작')
    top()
    check('active session sound control visible before plan', '배경 소리 선택', 'plan-focus-controls')
    reveal('여유 안에서 5분 더')
    before = remaining()
    tap('여유 안에서 5분 더')
    after = remaining()
    if not 280 <= after - before <= 300:
        raise AssertionError(f'Unexpected extension: {before} -> {after}')
    check('bounded extension changes countdown', '여유 안에서 5분 더', 'plan-04-extended')
    time.sleep(4)
    scenario('충전 15분')
    top(); reveal('계속'); tap('계속')
    reveal('여유 안에서 5분 더')
    if remaining() > 600:
        raise AssertionError('Charge update failed to cap session')
    node = find('여유 안에서 5분 더')
    if node is None or node.get('enabled') != 'false':
        raise AssertionError('Extension remains enabled without spare time')
    check('charge change shortens session and blocks extension', '여유 안에서 5분 더', 'plan-05-charge-limit')
    time.sleep(4)
    scenario('신호 대기')
    check('zero speed in drive stays locked', '신호 대기 중', 'plan-06-traffic-lock')
    scenario('주차')
    top(); reveal('나의 정차 플랜')
    check('parked plan recovers', '나의 정차 플랜', 'plan-07-recovered')
    adb('shell', 'wm', 'size', '720x1600')
    adb('shell', 'wm', 'density', '320')
    time.sleep(3)
    snapshot('plan-08-phone-portrait')
    time.sleep(4)
