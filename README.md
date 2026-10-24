# CabinOS ONE

**퓨처랩스** · 충전·정차 중 남은 시간에 맞춰 집중과 휴식을 계획하는 차량용 앱입니다.

**검토용 소스: 0.11.0-rc1.** AAOS 차량 읽기·세로 화면을 보완한 후보 버전입니다. 공식 Pleos SDK 포함 빌드와 Pleos Connect Emulator 실행은 다운로드 서버 접근 제한으로 **아직 검증하지 못했습니다**. 공모전 제출 완료 또는 실차 호환 인증을 의미하지 않습니다.

| 심사에 필요한 항목 | 안내 |
|---|---|
| 앱 이름·소개 | CabinOS ONE — StopSense의 출발 계획과 CabinOS 집중·휴식 세션을 연동 |
| 실행 방법 | 아래 APK 설치 → 일반 Android는 데모, Automotive 기기는 실제 차량 신호 모드로 시작 |
| 테스트 환경·설정 | JDK 21, Android SDK 35 / Build Tools 35.0.0, Android 10+; Pleos 프로필은 Vehicle 2.3.2 + NaviHelper 3.0.5 필요 |
| APK 위치 | [`apk/퓨처랩스_CabinOS_ONE_release.apk`](apk/퓨처랩스_CabinOS_ONE_release.apk) · [SHA-256](apk/SHA256SUMS) |

APK의 버전·SDK 포함 여부·검사 결과는 **[제출 검토표](docs/SUBMISSION_REVIEW_KO.md)**에서 확인하세요. 기본 빌드에는 AAOS 어댑터가 들어가지만 Pleos 전용 SDK는 포함하지 않습니다.

## 실행

```bash
adb install -r "apk/퓨처랩스_CabinOS_ONE_release.apk"
adb shell am start -n io.cabinos.one/.MainActivity
```

- **일반 Android 데모:** 연결 / 데모 → 충전 → P·정지 확인 후 집중 시작. 차량 상태와 충전 값은 시뮬레이션입니다.
- **AAOS/Pleos 기기:** 앱 내부 시뮬레이터를 숨기고 실제 속도·기어만 사용합니다. 차량 상태 → 차량 읽기 권한 확인. 유효한 속도 0 + P를 1.5초 확인해야 정차 기능이 열립니다. 서비스/권한/신호가 없으면 잠금을 유지합니다.
- **충전 시간:** 지원되는 차량의 배터리·충전 상태·잔여 시간을 읽습니다. 미지원 값은 만들지 않으며 시간 설정으로 수동 출발 계획을 사용할 수 있습니다.
- **차량 길 안내:** SDK 포함 빌드에서 Pleos 기본 내비의 검색·충전소·최근 목적지 화면으로 넘깁니다. CabinOS는 정차용 앱으로 구성하며 주행 허용(DO)을 선언하지 않습니다.
- **음성·위치:** 기기 STT/TTS 서비스와 언어팩, 마이크·위치 권한이 필요합니다. 지원하지 않는 기기에서는 화면 버튼을 사용합니다.

## 빌드와 테스트

Android Studio에서 저장소를 열고 SDK 35를 설치하세요. `ANDROID_HOME` 또는 Android Studio가 생성하는 `local.properties`로 SDK 위치를 지정합니다.

```bash
git clone --depth 1 --branch feat/pleos-vehicle-state https://github.com/hundol047/HYUNDAI.git
cd HYUNDAI
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease :app:lintDebug
# 공식 Maven 서버 접근이 가능한 환경에서만; 다운로드 실패 시 기본 빌드로 대체하지 않음
./gradlew -PpleosSdk=true :app:assembleRelease :app:testDebugUnitTest
```

`app/build/outputs/apk/debug/app-debug.apk`는 개발용이며, `release/app-release-unsigned.apk`는 서명 전 파일입니다. 제출 파일은 기존 팀 키로 서명합니다. SDK 설치·실제 기기 녹화·서명 절차는 [SDK 연동 안내](docs/PLEOS_VEHICLE_STATE_KO.md)에 있습니다.

## 검토할 문서와 대표 영상

1. [제출 검토표](docs/SUBMISSION_REVIEW_KO.md): 수정 항목, 테스트 근거, 남은 조건.
2. [SDK 지원 범위](docs/SDK_CAPABILITIES.md): 실제 신호·Pleos 포함 프로필·데모 구분.
3. [오픈소스 및 데이터 출처](docs/THIRD_PARTY.md): 사용 라이선스와 서비스 정책.

[대표 브라우저 실행 영상](docs/video/CabinOS_ONE_search_recovery_v0105.mp4)은 **기존 0.10.5 브라우저 녹화**입니다. APK/Pleos 실행 영상으로 표시하지 않습니다. [기존 웹 데모](https://hundol047.github.io/HYUNDAI/)도 0.10.5이며 이 브랜치의 차량 변경과 구분합니다. [이전 자료](docs/ARCHIVE.md)는 별도 색인으로 모았습니다.

## 오픈소스 라이선스

| 구성요소 | 라이선스·출처 |
|---|---|
| Kotlin·kotlinx.coroutines, AndroidX/Compose/Room, Hilt/Dagger, osmdroid | Apache-2.0 · [상세 목록](docs/THIRD_PARTY.md) |
| Leaflet, osrm-text-instructions | BSD-2-Clause · [상세 목록](docs/THIRD_PARTY.md) |
| OpenStreetMap 도로·장소 데이터 | ODbL, © OpenStreetMap contributors · [저작권](https://www.openstreetmap.org/copyright) |
| Pleos Vehicle / NaviHelper | SDK 제공자 이용 조건 적용. Apache/BSD 적용 대상과 구분 |

공개 Nominatim·OSRM·Overpass는 데모용이며 상용 SLA가 없습니다. 차량용 화면은 Pleos 내비게이션을 사용하도록 구성했습니다. 웹/일반 Android의 공용 서버 기능은 계약·자체 서버 설정이 필요합니다. 모든 국가·가게·도로의 완전성은 보장하지 않습니다.
