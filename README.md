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

공식 Maven 접근 실패 시 중지하며 SDK 없는 빌드로 대체하지 않습니다. Gradle release 출력은 서명 전 파일입니다. 팀 키 서명·공식 이미지 설치·Virtual Console·동일 APK 녹화 명령은 [실행 가이드](docs/PLEOS_VEHICLE_STATE_KO.md)를 따릅니다.

## 심사용 문서·영상

- [제출 검토표](docs/SUBMISSION_REVIEW_KO.md): 테스트 결과, APK 식별자, 장치 실행·공개 영상 상태.
- [핵심 기능 ↔ SDK API 매핑](docs/SDK_CAPABILITIES.md): 기획서에 사용할 기능 설명과 지원 범위.
- [오픈소스·데이터 출처](docs/THIRD_PARTY.md).

**[공식 Pleos 실제 release 실행 영상 · 96초](https://github.com/hundol047/HYUNDAI/releases/tag/v0.11.0-pleos-preview-7279b860)** — 동일 APK의 ADB 연속 녹화입니다. 실제 SDK SOC 수신과 P/0 화면을 확인할 수 있는 **진단 영상**이며, 신호 만료·Console 오류로 집중 세션과 전체 차량 상태 변경 시연은 미완료입니다. 영상의 미통과 항목도 공개했습니다. 브라우저 영상이나 Actions artifact를 Pleos 실행 증거로 대체하지 않습니다.

## 오픈소스 라이선스

| 구성요소 | 라이선스·출처 |
|---|---|
| Kotlin·coroutines, AndroidX/Compose/Room, Hilt/Dagger, Gson, osmdroid | Apache-2.0 · [원문·출처](docs/THIRD_PARTY.md) |
| Leaflet, osrm-text-instructions | BSD-2-Clause · [원문·출처](docs/THIRD_PARTY.md) |
| OpenStreetMap 도로·장소 | ODbL, © OpenStreetMap contributors · [저작권](https://www.openstreetmap.org/copyright) |
| Pleos Vehicle / NaviHelper | SDK 제공자 이용 조건. 위 오픈소스 라이선스의 적용 대상과 구분 |

## 제한 사항

- 공식 SDK 포함 0.11.0 / versionCode 19의 빌드·서명·232개 JVM 테스트와 실제 Pleos SDK 응답을 확인했습니다. release의 Gson/R8 오류를 수정했습니다. Console `503 / emulator_inactive`와 간헐적 정차 신호 만료가 남아 P→D·SOC 변화·집중 자동 종료의 전체 시연은 미완료입니다. 실제 목적지 ETA 계획·Pleos 분할 창도 추가 검증이 필요합니다. [검토표](docs/SUBMISSION_REVIEW_KO.md).
- DO를 선언하지 않은 정차 전용 앱입니다. 기본 내비의 지도·언어·지역 지원 범위를 따릅니다. 모든 도로·가게의 완전성을 보장하지 않습니다.
- 일반 Android/웹의 공용 Nominatim·OSRM·Overpass 기능은 데모이며 상용 SLA가 없습니다. 웹 데모는 공통 엔진의 브라우저 체험판이며 Pleos 차량 연동은 포함하지 않습니다.
- 저장소 소유 계정은 GitHub 주소에 표시됩니다. 블라인드 심사용 계정·링크 처리 방식은 주최 측 확인이 필요하며 기존 이력을 재작성하지 않았습니다.
