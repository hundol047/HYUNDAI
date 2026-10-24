# Pleos Vehicle State 연동 작업과 검증 범위

2026-10-24. 이 변경은 **아직 제출 완료본이 아닙니다.** AAOS 속도·기어 어댑터는 기본 프로필에서 빌드하고 테스트했습니다. 공식 Pleos SDK를 포함한 프로필은 AAR 다운로드 차단으로 컴파일하지 못했고, Pleos Connect Emulator 시연 영상도 아직 없습니다. 저장소의 기존 0.10.5 서명 APK·웹 영상은 이번 변경 이전 결과입니다.

## API를 선택한 근거

- [공식 API 호환성 안내](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)는 AAOS 표준 속성을 `VehiclePropertyIds`와 `CarPropertyManager`로 확인하도록 안내합니다. 속도·기어는 이 표준 API를 사용하며, Pleos 전용 SDK의 속도/기어 메서드를 만들어 내지 않았습니다.
- [Vehicle SDK 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)의 실제 좌표는 `ai.pleos.playground:Vehicle:2.3.2`, Maven 저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`입니다.
- [Brake capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/check-brake-pedal-status-capabilities)는 `(CapabilityConfig) -> Unit` / `(Exception) -> Unit` 콜백을 사용합니다. `accessType`이 READ 또는 READ_WRITE일 때만 조회합니다.
- [Brake Get](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/get-current-brake-pedal-status)는 `(Boolean?) -> Unit` / `(Exception) -> Unit`, 권한은 `pleos.car.permission.CAR_DRIVE`입니다. 눌림/해제 결과는 차량 상태 화면에 표시하며 **P 기어를 대신하지 않습니다**.
- [신청 안내](https://document.pleos.ai/docs/pleos-only/create-my-project)상 Vehicle State·NaviHelper는 신청 없이 사용 가능합니다. 이번 읽기 어댑터에 client secret을 요구하지 않습니다. NaviHelper 3.0.5의 기본 내비 화면 요청을 선택 프로필에 추가했습니다. 공식 AAR 컴파일과 실제 서비스 실행은 아직 미검증입니다.

SDK API Reference의 본문은 브라우저 JS로 표시되어, 공식 사이트의 해당 문서 JS에 포함된 Dokka 명세까지 확인했습니다. API 문서 확인은 실제 AAR 컴파일 또는 서비스 실행 검증을 대신하지 않습니다.

## 구현

| 경로 | 처리 |
|---|---|
| `AndroidCarPropertyTransport` | 비동기 Car 서비스 연결, 속성 지원/읽기 접근 확인, 이벤트 등록과 500ms 주기 재조회, 해제·오류 처리. IPC 작업은 별도 스레드 |
| `VehicleSignalGate` | m/s→km/h, 후진 속도의 절댓값, 기어 enum 매핑, 유한값·타임스탬프·역순 검증. CONTINUOUS 속도와 ON_CHANGE 기어의 시각을 구분 |
| `VehicleStateProvider` | 실제 신호만 전달. 3초 신선도 검사, 250ms 감시, 변화 없는 화면은 초당 한 번 갱신. 세대 번호로 종료 후 콜백 차단 |
| `PleosVehicleReader` / `src/pleos` | 실제 Vehicle 객체 initialize → capability → Brake Get → release. 3초 응답 제한, 오류 후 재조회. initialize만으로 연결 성공 표시 금지 |
| `CabinViewModel` | 선택된 출처만 중앙 엔진에 전달. Automotive 기기는 실제 모드 강제, 저장된 데모 P와 데모 실행 거부. 백그라운드 구독 해제 및 복귀 후 재확인 |
| UI | 차량 상태/SDK 포함 여부/브레이크 조회 결과 표시, 알 수 없는 배터리는 `—`. 서비스·권한 실패 시 정차 기능 잠금 |

읽기 권한: `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN`, `pleos.car.permission.CAR_DRIVE`. `android.car` 라이브러리는 optional이므로 일반 Android에서도 설치할 수 있으며, 비자동차 기기에서 Car 클래스를 실행하지 않습니다. 차량 쓰기/공조/조명 API는 없습니다. 주차는 유효한 두 신호, 속도 0, P 기어, 기존 엔진의 1.5초 확인이 모두 필요합니다.

배터리·충전 상태·충전 잔여 초를 지원되는 AAOS 속성에서 선택적으로 읽습니다. Pleos EvBattery의 capability→getEvBatteryStateOfCharge도 별도로 조회합니다. 실제 모드에서 데모 68%나 40분 값을 생성하지 않습니다. GPS는 별도 Android 위치 어댑터이며 주차의 근거로 사용하지 않습니다.

## 빌드 프로필

기본 프로필은 AAOS 속도·기어·에너지 어댑터만 포함하고, 앱에 **공식 Pleos SDK 미포함**을 표시합니다. 공식 SDK의 가짜 클래스나 가짜 성공 값은 없습니다.

```bash
source scripts/env.sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

공식 SDK 포함 프로필은 아래 옵션이 필요합니다. 네트워크 복구 후 실제 AAR로 반드시 빌드·테스트해야 합니다. 현재 이 프로필의 성공 기록은 없습니다.

```bash
./gradlew -PpleosSdk=true :app:testDebugUnitTest :app:assembleDebug
./gradlew -PpleosSdk=true :app:assembleRelease
./gradlew -PpleosSdk=true :app:lintDebug
# 위 검증과 실제 장치 검증 후, 기존 팀 키로 제출 APK 생성
CABINOS_PLEOS_SDK=true bash scripts/package-submission.sh
```

SDK 다운로드 실패 시 기본 프로필로 자동 대체하지 않습니다. 제출 APK 파일명은 `apk/퓨처랩스_CabinOS_ONE_release.apk`이며, SDK 포함 빌드와 실제 실행을 확인한 뒤 갱신해야 합니다. 작업 브랜치의 후보 APK 버전/해시는 [검토표](SUBMISSION_REVIEW_KO.md)를 확인하세요. 공개 main의 기존 APK는 0.10.5입니다.

## Pleos Connect Emulator에서 남은 실행 절차

1. [공식 설치 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 SDK Manager에 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`을 등록하고 Pleos Connect Emulator 2.1.0/API 34 이미지를 설치합니다. 일반 Android Automotive 이미지 또는 이름만 Pleos로 바꾼 AVD는 증거로 사용하지 않습니다.
2. SDK 포함 APK를 설치합니다. 테스트용 에뮬레이터에서는 `adb -s emulator-5554 install -r -g app/build/outputs/apk/debug/app-debug.apk`로 선언된 런타임 권한도 부여할 수 있습니다. 실제 기기는 정차 중 시스템 권한 설정에서 확인합니다. 권한/속성 미지원 시 앱이 UNKNOWN으로 남는지 확인합니다.
3. [공식 Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)을 사용합니다. `adb -s emulator-5554 forward tcp:8088 tcp:8088` 후 개발 PC의 localhost:8088에서 상태를 바꿉니다. 앱 자체의 시뮬레이터로 입력하지 않습니다.
4. 화면에서 실제 모드임을 확인합니다. 속도 0/P → 1.5초 이후 정차 허용, 브레이크 눌림/해제 → SDK 조회 값 변화, D/0 → 신호 대기와 잠금, D/주행 속도 → 주행과 잠금, P/0 → 재확인 순으로 시연합니다. 화면 이탈·복귀/권한 해제/서비스 끊김에서도 이전 P 값으로 열리지 않는지 검사합니다.
5. 실제 기기 프레임을 연속 녹화합니다. `--apk`와 설치된 APK의 SHA-256이 같아야 하며, 새 출력 폴더를 사용합니다.

```bash
python3 scripts/record-vehicle-emulator.py \
  --serial emulator-5554 \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --seconds 90 --output artifacts/pleos-device-run-01
```

스크립트는 `adb shell screenrecord`의 MP4, APK 해시, 기기 fingerprint, 해당 앱 프로세스의 실제 차량 상태 로그, 검사 결과를 저장합니다. 초기화만 성공하거나 SDK 값이 없는 로그는 통과시키지 않습니다. 최종 제출 영상은 서명된 release APK로도 다시 실행·녹화하고, 파일 경로/버전과 일치시켜야 합니다. 로그 검사만으로 영상 내용이나 공식 이미지 사용을 확정하지 않으며 `submissionReady=false`를 유지합니다. **현재 이 녹화 명령은 장치에서 실행하지 않았습니다.**

## 이번 실행 근거와 남은 조건

- 기본 프로필: 공통 엔진 120개, Android 92개 테스트 통과(추가 차량/SDK 래퍼/Automotive UI 테스트 20개 포함). 합성 SDK client와 신호 입력을 사용했으며 실제 차량 응답이 아닙니다.
- 녹화 증거 검사 로직: Python 4개 테스트 통과. 실제 동영상 생성과 구분합니다.
- 기본 프로필 debug/release 빌드 성공, lint 오류 0개·기존 경고 5개. [기계 판독 결과](validation/pleos-vehicle-state.json).
- 공식 SDK 프로필: 미컴파일. Pleos 에뮬레이터·실차 실행·새 시연 영상: 미실행.
- 공식 SDK/이미지 공통 서버 `nexus-playground.pleos.ai`: 프록시 CONNECT 403. 허용 도메인을 환경 설정 초안에 추가했지만 실행 환경 적용은 확인되지 않았습니다. 환경 설정에서 초안을 저장·게시한 뒤 다운로드를 재검사해야 합니다.
- 현재 머신에는 `/dev/kvm`도 없습니다. 공식 이미지 확보 후 소프트웨어 실행 가능 여부를 확인하고, 실행 성능이 부족하면 가상화가 가능한 개발 PC에서 위 절차를 수행해야 합니다. KVM 부재만으로 실행 불가능하다고 단정하지 않았습니다.

완료 기준은 **공식 AAR를 포함한 release 빌드, 공식 Pleos 이미지에서 실제 속도/기어 및 SDK 브레이크 값 수신, 안전 전환 검증, 동일 APK 연속 실행 영상, 제출 APK 갱신**입니다. AAOS 코드만 빌드한 상태로 이 기준을 충족했다고 표시하지 않습니다.

## 0.11.0-rc1 추가 API

- [EvBattery Get](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-battery-state-of-charge): `(Int?) -> Unit`, 배터리 %; `pleos.car.permission.CAR_ENERGY` normal 권한. capability의 READ/READ_WRITE 확인 뒤 호출하며 오류·시간 초과는 Brake와 별개입니다.
- [NaviHelper 초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release): onConnected/onFailed 콜백과 리스너 등록·해제. `requestNavigationScreen`으로 `Foreground`, `SearchPlace(keyword)`, `NearbyChargingStations`, `RecentDestinations`를 요청합니다. 요청 전 P·정지와 연결 상태를 다시 검사합니다.
- AAOS 에너지는 선택 속성입니다. 미지원 시 속도·기어의 안전 판정을 버리지 않으며 배터리는 `—`, 충전 잔여 시간은 없음으로 표시합니다. INFO_EV_BATTERY_CAPACITY의 static timestamp 0은 허용하고 최신 재조회로 유효성을 확인합니다.
- 차량 앱은 정차 구성입니다. DO를 임의 선언하지 않으며 기본 Pleos 내비가 길 안내를 맡습니다. 실제 검색 지역·데이터·주행 안내 정책은 기본 내비의 기능 범위에 따릅니다.

위 API 원문을 확인한 것과 AAR 컴파일 성공은 다릅니다. 현재 SDK 포함 프로필은 여전히 미컴파일이며 가짜 SDK 클래스로 대체하지 않았습니다.
