# CabinOS ONE

**퓨처랩스** · 차량의 충전 상태와 다음 도착 일정에 맞춰 집중·휴식 시간을 조정하는 정차·충전 전용 앱입니다. StopSense의 출발 계획과 CabinOS 세션이 같은 시간표를 사용합니다.

| 제출 항목 | 안내 |
|---|---|
| 앱 이름·한 줄 소개 | CabinOS ONE — 멈춘 시간을 다음 여정에 맞추는 차량용 집중·휴식 앱 |
| 실행 방법 | 아래의 서명 release APK 설치 명령. Automotive는 실제 차량 모드, 일반 Android는 데모 |
| 테스트 환경·필요 설정 | JDK 21, Android SDK 35 / Build Tools 35.0.0, minSdk 29. 공식 Vehicle 2.3.2 + NaviHelper 3.0.5 포함. 차량 검증 대상: Pleos Connect 2.1.0 / API 34 |
| APK 위치 | [`apk/퓨처랩스_CabinOS_ONE_release.apk`](apk/퓨처랩스_CabinOS_ONE_release.apk) · 0.11.0 · [SHA-256](apk/SHA256SUMS) |

## 실행

```bash
sha256sum -c apk/SHA256SUMS
adb install -r "apk/퓨처랩스_CabinOS_ONE_release.apk"
adb shell am start -n io.cabinos.one/.MainActivity
```

- **차량:** 차량 읽기 권한을 허용하고 유효한 속도 0 + P를 1.5초 확인해야 열립니다. D/0·신호 소실 시 잠급니다. 앱 내부 데모 조작판은 숨깁니다.
- **충전 계획:** 실제 충전 잔여 시간 또는 수동 출발 시간을 설정하세요. SOC 계획 목표에 도달하면 활동을 종료합니다. 차량의 충전 목표 자체는 변경하지 않습니다.
- **다음 여정:** 차량 내비에서 목적지를 설정하고 앱으로 돌아와 도착 목표를 선택하면 남은 주행 시간과 출발 준비 시간을 반영합니다. 주행 안내는 기본 내비게이션이 담당합니다.
- **일반 Android 데모:** 연결 / 데모 → 충전 → 집중 시작. 차량 값은 시뮬레이션입니다. 음성 기능은 기기 STT/TTS 엔진·언어팩과 마이크 권한이 필요합니다.

## 소스 빌드·검증

Android Studio에서 SDK 35를 설치하고 `ANDROID_HOME` 또는 `local.properties`에 SDK 경로를 설정하세요.

```bash
git clone --depth 1 --branch main https://github.com/hundol047/HYUNDAI.git
cd HYUNDAI
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

기본 빌드와 Android Studio 동기화는 공식 SDK를 포함합니다 (`gradle.properties`의 `pleosSdk=true`). `-PpleosSdk=false`는 SDK 없는 데모 개발에만 사용하며 차량 검증·제출에는 사용할 수 없습니다. 공식 Maven 접근 실패 시 중지하며 SDK 없는 빌드로 대체하지 않습니다. Gradle release 출력은 서명 전 파일입니다. 팀 키 서명·공식 이미지 설치·Virtual Console·동일 APK 녹화 명령은 [실행 가이드](docs/PLEOS_VEHICLE_STATE_KO.md)를 따릅니다.

## 심사용 문서·영상

- [공식 설치 구성·원격 실행 검증](docs/PLEOS_SETUP_VERIFICATION_KO.md): 4GB·Cold boot·KVM의 실제 실행 결과와 재현 명령.
- [제출 검토표](docs/SUBMISSION_REVIEW_KO.md): 테스트 결과, APK 식별자, 장치 실행·공개 영상 상태.
- [핵심 기능 ↔ SDK API 매핑](docs/SDK_CAPABILITIES.md): 기획서에 사용할 기능 설명과 지원 범위.
- [오픈소스·데이터 출처](docs/THIRD_PARTY.md).

**[Pleos 실제 차량 시나리오 13/13 통과 · 수정본](docs/PLEOS_SETUP_VERIFICATION_KO.md)** — P/D·주행 잠금, 실제 SDK SOC·충전 상태 변경, 집중 시작 뒤 SOC 목표에 따른 종료를 확인했습니다. [검증 APK](apk/CabinOS_ONE_Pleos_validation.apk)와 [ADB 연속 원본 영상](docs/validation/pleos-vehicle-20261107/device-screen.mp4)을 보존했습니다. 검증 APK는 임시 키 서명으로 새 AVD에 설치하며, 기존 배포 서명 APK와 구분합니다. [기존 0.11.0 진단 영상](https://github.com/hundol047/HYUNDAI/releases/tag/v0.11.0-pleos-preview-7279b860)은 수정 전 기록입니다.

## 오픈소스 라이선스

| 구성요소 | 라이선스·출처 |
|---|---|
| Kotlin·coroutines, AndroidX/Compose/Room, Hilt/Dagger, Gson, osmdroid | Apache-2.0 · [원문·출처](docs/THIRD_PARTY.md) |
| Leaflet, osrm-text-instructions | BSD-2-Clause · [원문·출처](docs/THIRD_PARTY.md) |
| OpenStreetMap 도로·장소 | ODbL, © OpenStreetMap contributors · [저작권](https://www.openstreetmap.org/copyright) |
| Pleos Vehicle / NaviHelper | SDK 제공자 이용 조건. 위 오픈소스 라이선스의 적용 대상과 구분 |

## 제한 사항

- 수정본은 공통 134개·Android 102개·검증 도구 13개 테스트와 실제 Pleos 차량 시나리오 13개를 통과했습니다. 원격 소프트웨어 그래픽의 UI 지연·안전 일시정지, 초기 vendor 서비스·입력기 오류는 남아 있습니다. 공식 외장 GPU 성능, 실제 목적지 ETA·마이크 음성 인식·분할 창은 별도 검증 대상입니다. [최신 검증 범위](docs/PLEOS_SETUP_VERIFICATION_KO.md).
- 기존 SDK 통합·배포 서명 0.11.0은 PR #1로 main에 병합됐습니다. 이번 수정 소스와 검증 APK는 [PR #3](https://github.com/hundol047/HYUNDAI/pull/3)에 있으며 main 병합·정식 배포는 별도입니다.
- DO를 선언하지 않은 정차 전용 앱입니다. 기본 내비의 지도·언어·지역 지원 범위를 따릅니다. 모든 도로·가게의 완전성을 보장하지 않습니다.
- 일반 Android/웹의 공용 Nominatim·OSRM·Overpass 기능은 데모이며 상용 SLA가 없습니다. 기존 웹 데모는 0.10.5입니다.
- 저장소 소유 계정은 GitHub 주소에 표시됩니다. 블라인드 심사용 계정·링크 처리 방식은 주최 측 확인이 필요하며 기존 이력을 재작성하지 않았습니다.
