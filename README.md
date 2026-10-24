# CabinOS ONE

**퓨처랩스** · 차량의 충전 상태와 다음 도착 일정에 맞춰 집중·휴식 시간을 조정하는 정차·충전 전용 앱입니다. StopSense의 출발 계획과 CabinOS 세션이 같은 시간표를 사용합니다.

소스 버전: **0.11.1 (20)**. `apk/`의 제출 APK는 아직 **0.11.0 (19)**이며, 팀 키로 새 버전을 서명하기 전까지 바뀌지 않습니다. **현재 APK에는 최신 수정이 없으므로 새 버전으로 배포하지 마세요.** 서명 후 [배포 파일 검사](docs/RELEASE_NOTES_0.11.1.md)를 먼저 실행해야 합니다.

| 제출 항목 | 안내 |
|---|---|
| 앱 이름·한 줄 소개 | CabinOS ONE — 멈춘 시간을 다음 여정에 맞추는 차량용 집중·휴식 앱 |
| 실행 방법 | 아래의 서명 release APK 설치 명령. Automotive는 실제 차량 모드, 일반 Android는 데모 |
| 테스트 환경·필요 설정 | JDK 21, Android SDK 35 / Build Tools 35.0.0, minSdk 29. 공식 Vehicle 2.3.2 + NaviHelper 3.0.5 포함. 차량 검증 대상: Pleos Connect 2.1.0 / API 34 |
| APK 위치 | [`apk/퓨처랩스_CabinOS_ONE_release.apk`](apk/퓨처랩스_CabinOS_ONE_release.apk) · 0.11.0 · [SHA-256](apk/SHA256SUMS) |

## 실행

```bash
sha256sum -c apk/SHA256SUMS
adb install -r -g "apk/퓨처랩스_CabinOS_ONE_release.apk"
adb shell am start -n io.cabinos.one/.MainActivity
```

`-g`는 에뮬레이터 시험 설치 시 런타임 권한을 부여합니다. `-g` 없이 설치하면 앱 안에서 차량 읽기 권한을 허용해야 합니다. OEM 권한 정책은 별도로 적용됩니다. 0.11.1 소스는 차량에서 최초 한 번 자동 요청하며, 거부 후에는 복귀·재시작해도 자동으로 다시 묻지 않습니다. 추가 요청은 사용자가 권한 버튼을 눌렀을 때만 진행합니다. 다시 묻지 않도록 거부한 경우 이 버튼으로 앱 설정을 열어 권한을 허용합니다.

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

## Pleos SDK 사용 기능

| 기능 | 실제 읽기 API | 현재 검증 |
|---|---|---|
| SOC 목표에 맞춘 활동 종료 | EvBattery.getEvBatteryStateOfCharge / getEvChargeSwitchState | 0.11.0 실제 초기 응답 확인; 변화 시나리오 미완료 |
| 도착 목표 → 출발 계획 | NaviHelper.getDestinationInfo / onDestinationInfo | 실제 경로 ETA 전체 시연 필요 |
| 동승자 → 함께 휴식·오디오 제안 | Seat.getCurrentSeatOccupancy | 0.11.1 SDK 컴파일·단위 테스트; 기기 미검증 |
| 휴식 전 문·창문·공조 확인 | Door / Window / HVAC 읽기 | 0.11.1 SDK 컴파일·단위 테스트; 기기 미검증 |
| 정차 중 워셔액 확인 | Safety.hasLowWasherFluidWarning | 0.11.1 SDK 컴파일·단위 테스트; 기기 미검증 |

정확한 메서드·권한·공식 문서·미지원 범위는 [SDK 매핑표](docs/SDK_CAPABILITIES.md)를 참고하세요.
저장된 계획을 읽지 못하면 화면에 오류를 표시하고 자동 저장을 중지합니다. 저장 공간을 확인한 뒤 앱을 다시 시작해 주세요. [배포 전 오류 점검](docs/PREDEPLOY_RUNTIME_AUDIT_KO.md)에 재현 결과를 정리했습니다.

[전체 시연 실행 절차](scripts/demo/pleos-full-scenario.md)와 [0.11.1 배포 준비](docs/RELEASE_NOTES_0.11.1.md)는 사람의 서명·실제 기기 검증 이후 완료해야 합니다.

## 제한 사항

- 현재 APK·공개 진단 영상은 0.11.0입니다. 0.11.1 소스의 팀 키 서명과 같은 APK 전체 시연은 미완료입니다.
- 공식 에뮬레이터의 Console 503·간헐적 속도 만료가 남아 있습니다. 수신 시각 대안은 기본값 `false`입니다.
- DO 미선언 정차 전용 앱입니다. 웹·폰의 OSM 내비는 부가 데모이며 공용 서비스의 가용성·장소 완전성을 보장하지 않습니다.
- GitHub 소유 계정·기존 커밋 작성자가 노출됩니다. 블라인드 적합성은 주최 측 확인이 필요합니다.
