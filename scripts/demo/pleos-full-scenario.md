# Pleos 전체 시나리오 실측

KVM 사용 가능한 Linux PC에서 공식 Pleos Connect 2.1.0 / API 34 이미지를 설치합니다.
공식 배포 목록: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
공식 안내: https://document.pleos.ai/ (Connect Emulator / Virtual Console).
Console HTTP 제어 경로의 공식 문서 근거는 확인하지 못했습니다. **자동 HTTP 제어는 하지 않습니다.**
각 단계는 http://localhost:8088 의 공식 Console 화면에서 수동으로 변경합니다.
503/emulator_inactive이면 중단하고 서비스 로그와 기기 상태를 확인하세요.

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_AVD_HOME="$HOME/.android/avd"
python3 scripts/install-pleos-emulator.py --archive /path/to/official-2.1.0.zip --avd-home "$ANDROID_AVD_HOME"
"$ANDROID_HOME/emulator/emulator" -list-avds
export PLEOS_AVD=공식_AVD_이름
scripts/demo/run-full-scenario.sh "apk/퓨처랩스_CabinOS_ONE_release.apk" artifacts/full-scenario
python3 scripts/demo/analyze-signal-log.py artifacts/full-scenario/vehicle.log
```

스크립트는 cold boot, `adb install -r -g`, `adb forward tcp:8088 tcp:8088`을 수행합니다.
APK를 기기에서 다시 내려받아 원본 SHA-256과 대조합니다. 서명 키는 필요하지 않습니다.
설치된 fingerprint/API가 공식 버전과 다르거나 해시가 다르면 중단합니다.

녹화 전 Console 조작을 연습하고 창을 나란히 배치하세요. 녹화는 **120초 단일 screenrecord**입니다.
P/0 3초 → 집중 15분 → D/0 → D/40 → P/0 3초 → 목표 미만 SOC에서 새 세션 시작 후 목표 초과 →
기본 내비 목적지 설정 및 앱 도착 목표 설정 순서입니다. 각 조작 뒤 터미널에서 Enter를 누릅니다.
Enter는 성공 확인이 아닙니다. 해당 구간의 실제 차량 로그가 조건을 증명해야 JSON이 통과합니다.
시간 초과·미실행·SDK 미수신·가짜 차량 데이터·누락 로그는 실패입니다.

출력: continuous.mp4, vehicle.log(DEBUG 포함), fingerprint.txt, apk-sha256.json,
단계별 원시 이벤트 JSON, verdict.json. 영상 재생과 각 단계 UI 확인도 필요합니다.
현재 저장소 APK 0.11.0에는 이후 진단 필드가 없어 일부 단계는 실패할 수 있습니다.
사람이 SDK 포함 새 버전을 팀 키로 서명한 뒤 같은 APK로 다시 실행해야 합니다.
검증 통과 이전에 이 영상을 제출용 전체 시나리오 성공 영상으로 표시하지 마세요.

녹화 검증에는 `ffprobe`(FFmpeg)가 필요합니다. 녹화 프로세스의 정상 종료, 영상 길이 60~120초,
모든 단계가 실제 영상 시간 안에 포함되는지도 확인합니다. 초기 UNKNOWN은 허용하지만,
P 수신 후 1.5초 전 해제되거나 정차 확인 구간 로그가 부족하면 실패합니다.
