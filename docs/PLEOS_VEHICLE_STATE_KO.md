# Pleos 실행·녹화 재현 절차

공식 Vehicle 2.3.2 / NaviHelper 3.0.5 포함 빌드를 사용합니다. API 매핑은 [SDK 지원 범위](SDK_CAPABILITIES.md), 검증 완료/미완료와 APK 해시는 [제출 검토표](SUBMISSION_REVIEW_KO.md)에 있습니다.

## 1. 개발 PC 준비

Android Studio, JDK 21, Python 3, Android SDK Platform 35 / Build Tools 35.0.0 / Emulator / Platform Tools를 설치합니다. Pleos 실행 이미지는 API 34입니다. 아래 명령은 Linux/macOS 셸 기준이며 Windows에서는 Android Studio Device Manager와 PowerShell의 해당 SDK 경로를 사용합니다.

```bash
export ANDROID_HOME="$HOME/Android/Sdk" # 자신의 SDK 경로로 수정
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export ANDROID_AVD_HOME="$HOME/.android/avd"
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

공식 Maven 접근 실패 시 빌드를 중지합니다. AAOS 기본 프로필로 바꿔 제출하지 않습니다. Linux는 BIOS 가상화 + `/dev/kvm` 접근이 필요합니다. `emulator -accel-check`로 확인하세요. KVM 없는 클라우드의 소프트웨어 실행은 로컬 PC 실행 성능을 대표하지 않습니다. macOS ARM은 공식 이미지/호스트 아키텍처 지원을 먼저 확인하세요.

## 2. 공식 Pleos 이미지 설치

Android Studio SDK Manager → SDK Update Sites에 아래 공식 인덱스를 추가하고 **Pleos Connect v2.1.0 / API 34**를 설치합니다. Device Manager에서 공식 Pleos 기기를 선택합니다.

`https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

Linux x86_64의 재현용 CLI 설치도 제공합니다. 저장 공간은 압축 파일 약 4.1GB와 시스템 이미지 논리 크기 약 18.3GB, 데이터·작업 공간이 필요합니다. 스크립트는 원본 바이트를 유지하는 sparse 추출을 사용합니다.

```bash
python3 scripts/install-pleos-emulator.py \
  --archive "$HOME/.cache/pleos-connect-2.1.0.zip" \
  --sdk "$ANDROID_HOME" --avd-home "$ANDROID_AVD_HOME"
adb start-server # 에뮬레이터 실행 전에 호스트 인증키 준비
emulator -avd CabinOS_Pleos_21 -no-snapshot -gpu swiftshader_indirect
```

공식 인덱스의 archive SHA-1: `b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa`, 크기 4,087,751,964 bytes. 일반 AAOS 이미지를 이름만 바꾼 AVD는 인정하지 않습니다.

## 3. 같은 서명 release APK 설치

```bash
sha256sum -c apk/SHA256SUMS
adb devices
adb -s emulator-5554 install -r -g "apk/퓨처랩스_CabinOS_ONE_release.apk"
adb -s emulator-5554 shell am start -n io.cabinos.one/.MainActivity
adb -s emulator-5554 forward tcp:8088 tcp:8088
```

실제 serial을 사용하세요. `-g`는 에뮬레이터 시험용 런타임 권한 부여이며 실제 차량의 OEM 권한 정책을 우회하지 않습니다. 속도/기어·SDK 읽기 권한과 지원 여부를 차량 상태 화면에서 확인합니다. 신호가 없으면 UNKNOWN 잠금이 정상입니다.

## 4. Virtual Console과 90초 연속 녹화

개발 PC에서 `http://localhost:8088`을 열고 공식 Virtual Console로 차량 값을 변경합니다. 앱의 데모 모드를 사용하지 않습니다.

```bash
python3 scripts/record-vehicle-emulator.py \
  --serial emulator-5554 --apk "apk/퓨처랩스_CabinOS_ONE_release.apk" \
  --seconds 90 --output artifacts/pleos-release-take-01
```

녹화 중 순서:

1. 시작은 신호 확인/잠금 화면. P + 속도 0 설정 → 1.5초 후 정차 화면 확인.
2. D + 속도 0 → 신호 대기 잠금. D + 주행 속도 → 잠금. OS가 정차 전용 앱을 가릴 수도 있으므로 기본 화면과 앱 로그를 함께 확인.
3. P + 속도 0으로 복귀 → 다시 1.5초 확인. SOC 68%, 충전 ON 및 지원되는 충전 잔여 시간을 설정.
4. 앱에서 SOC 목표 80%, 대기 40분, 집중 시작. Console SOC 80% 변경 → 출발 준비/세션 종료 확인. 충전 OFF → 충전 예산 제거 확인.
5. 내비에 실제 목적지를 설정 후 앱으로 돌아와 도착 목표를 정함. 목적지 시간 수신 여부·준비 시간을 확인. 이미지의 내비 서비스가 응답하지 않으면 미검증으로 기록.
6. 앱 이탈·복귀 → 신호 재확인, 이전 P를 그대로 신뢰하지 않음.

녹화기는 `adb shell screenrecord`의 연속 프레임을 사용하고 설치된 APK를 다시 pull하여 전달한 APK와 SHA-256을 대조합니다. 이미지 fingerprint, SDK SOC/충전 응답·계획 변화·잠금 증거를 남깁니다. 스크린샷을 이어 붙이지 않습니다. 검사가 통과해도 영상과 공식 이미지 정체성은 별도 검토가 필요합니다. 출력의 `submissionReady=false`는 사람이 확인하기 전 유지합니다.

MP4를 직접 재생하고 화면 잘림·클릭 반응·영상 길이·끊김을 검토한 뒤 `docs/video/` 또는 GitHub Release에 올립니다. 공개 링크는 로그인 없이 확인하고 Actions artifact를 대표 영상 링크로 쓰지 않습니다.

## 5. 서명과 제출

검증을 마친 소스를 팀 키로 서명합니다. 키와 비밀번호는 저장소 밖에 두며 기존 팀 키를 유지하세요.

```bash
export CABINOS_SIGNING_DIR="$HOME/.local/share/cabinos-team-signing" # 기존 release.p12 / password 위치
CABINOS_PLEOS_SDK=true bash scripts/package-submission.sh
sha256sum -c apk/SHA256SUMS
```

서명된 파일로 3~4단계를 다시 실행해야 합니다. 최종 파일명은 `apk/퓨처랩스_CabinOS_ONE_release.apk`입니다. PR의 CI와 공개 영상·APK 해시를 확인한 후에만 main 병합을 검토합니다. 병합은 별도 승인 대상입니다.


## 6. Console 503 / SDK 값 누락 재현 시

공식 이미지가 부팅되고 APK가 실행돼도 차량 데이터 서비스가 정상이라는 뜻은 아닙니다. 이 작업의 KVM 실행에서는 권한 부여와 AAOS P/0 수신은 확인했지만 Console 503·EvBattery 읽기 실패가 남았습니다. 다음 명령으로 로컬 PC의 동일 이미지와 비교하세요.

```bash
adb start-server
adb -s emulator-5554 forward tcp:8088 tcp:8088
curl --fail-with-body http://localhost:8088/vehicle/state
curl --fail-with-body -X PUT 'http://localhost:8088/vehicle/gear?gear=Park'
python3 scripts/inspect-pleos-device.py --serial emulator-5554 \
  --apk "apk/퓨처랩스_CabinOS_ONE_release.apk" --output artifacts/pleos-local-diagnostics
```

`identity.json`, `vehicle-logcat.txt`, `vehicle-status.xml`, Console 응답을 비교합니다. SOC/스위치가 null이거나 Console 입력이 실패하면 녹화 완료로 표시하지 마세요. Android Studio에서 공식 Pleos 하드웨어 프로필·Cold boot·4 GB RAM으로 만든 AVD에서도 재현되면, 실제 오류와 이미지/SDK 버전을 주최 측 기술 지원에 전달할 자료로 사용하세요. 진단 원문에는 기기 환경 정보가 있을 수 있으므로 블라인드 제출 자료에 그대로 첨부하지 않습니다. 앱 소스의 신호 검증을 완화하거나 SDK 권한 검사를 우회하지 않습니다.
