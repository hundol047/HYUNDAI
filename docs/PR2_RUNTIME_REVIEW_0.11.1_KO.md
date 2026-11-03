# PR #2 — 0.11.1 변경·검증 보고

작업 브랜치는 `fix/vehicle-runtime-issues`입니다. main 직접 push·이력 재작성·제출 APK 교체를 하지 않았습니다.
현재 `apk/`는 0.11.0 (19), 소스는 0.11.1 (20)입니다. 새 기능을 실제 에뮬레이터에서 검증했다고 주장하지 않습니다.

## 항목별 변경과 재현 테스트

아래 테스트는 실패를 확인한 뒤 구현해 통과시켰습니다. 문서·버전 변경은 별도 동작 테스트 대신 파일 검사를 사용했습니다.

| 항목 | 주요 변경 파일 | 추가·보강 테스트와 결과 | 실제 Pleos 검증 |
|---|---|---|---|
| 1a 전체 시연 | `scripts/demo/run-full-scenario.sh`, `full_scenario.py`, `pleos-full-scenario.md`, `VehicleDiagnostics.kt` | `ScenarioEvidenceTest` 6개, `scenarioEvidenceIncludesSessionAndNavigationInputs` 1개 통과 | 아니요: KVM PC 필요 |
| 1b 로그 분석 | `scripts/demo/analyze-signal-log.py`, `VehicleSignalGate.kt`, `VehicleStateProvider.kt` | `SignalAnalysisTest` 5개, `diagnosticTimingIsThrottledAndCannotRefreshOldSpeed` 보강 통과 | 아니요 |
| 1c 비활성 대안 | `VehicleSignalGate.kt`, `AndroidCarPropertyTransport.kt`, `app/build.gradle.kts` | `VehicleReceiptFreshnessTest` 4개 통과: 플래그 양쪽·P/0·이동·D·poll 실패·구독·연결·1초 만료 | 아니요; 기본값 false |
| 2a capability 재확인 | `PleosVehicleReader.kt` | `unreadableCapabilitiesRetryAfterThirtySecondsAndRecover`, `stillUnreadableCapabilitiesRetryOnlyEveryThirtySeconds` 통과 | 아니요 |
| 2b 권한 거부·동일 결과 | `CabinApp.kt`, `RuntimePreferences.kt`, `VehiclePermissionResult.kt` | `deniedVehiclePermissionDoesNotPromptAgainOnForegroundReturn`, `unchangedGrantedEnergyPermissionDoesNotReconnect`, `VehiclePermissionResultTest` 2개 통과 | Robolectric만 |
| 2c 버전 | `app/build.gradle.kts`, README, 제출 검토표 | 0.11.1/20 소스와 0.11.0/19 APK 구분; apk diff 없음 | APK 변경 없음 |
| 3a 동승자 제안 | `PleosCabinReader.kt`, 양쪽 SDK factory, `CabinAdvicePanel.kt`, DI·ViewModel | `passengerReadIsOptionalAndClearedWhenBackgrounded`, `unsupportedOrTimedOutPassengerDoesNotInventOccupancy`, `cabinAdviceIsVisibleOnlyAfterParkingConfirmation` 통과 | 아니요 |
| 3b 실내 확인 | SDK cabin factory·reader·Manifest·카드 | `readinessClearsOnUnsupportedAndStopWithoutBlockingPassenger` 및 카드 UI 테스트 통과 | 아니요 |
| 3c 워셔액 점검 (표시등 제외) | SDK cabin factory·reader·Manifest·카드 | `careCardShowsOnlyVehicleValuesAndClearsOnLoss` 통과 | 아니요 |
| 4a 웹 정차 중심 | `index.html`, `web.js`, `app.css`, 웹 검증 조작 단계 | `parked story is primary and supplementary demos start collapsed` 포함 기본 16개·지도 16개 통과 | 브라우저 데모 |
| 4b 폰 데모 표기 | `Dashboards.kt`, `NavigationScreen.kt` | `activityLaunchAndDrivingSafetyAreRendered`, `navigationNamesakeShowsActualDistanceAndHidesItWithoutUsableGps` 보강 통과 | Robolectric만 |
| 5a 도착 시각 | 엔진, ViewModel, `ArrivalClockControls.kt`, 차량 내비 화면 | `absoluteArrivalTargetPreservesClockTimeAndRejectsPastOrDriving`, `ArrivalClockTest` 2개, `arrivalWithoutRouteExplainsDisabledControls` 통과 | 아니요 |
| 5b 집중 프리셋 | `SessionScreens.kt`, `CompactDashboard.kt` | `focusPresetsAreAccessibleAndDisabledOutsideParking`, 좁은 창 `portraitDashboardSessionAndSettingsRemainReachable` 보강 통과 | Robolectric만 |
| 5c 도보 예약 초기화 | `UnifiedVehicleContextEngine.kt` | `drivingClearsOldWalkingReservationBeforeNextStop` 통과 | 단위 테스트 |
| 5d 음성 미지원 | `Providers.kt`, 내비·어시스턴트·설정·메뉴 | `missingTtsOrRecognizerDisablesVoiceEntry`, `missingVoiceServicesShowCommandsWithoutMicrophone` 통과 | Robolectric만 |
| 6a 제출 README | README·제출 검토표·설정 설명 | 필수 4항목·-g·SDK 기능·라이선스·제한 4개 확인 | 문서 검사 |
| 6b main 참조 | README, 수동 preview workflow | 과거 브랜치 링크 없음; 오래된 workflow 브랜치 제한도 main으로 갱신 | workflow 실행·게시 안 함 |
| 6c Release 초안 | `RELEASE_NOTES_0.11.1.md` | APK 해시 확인·동일 APK 녹화·수동 게시 절차 작성 | Release 미게시 |
| 6d 개인정보 | `PRIVACY_SCAN_0.11.1_KO.md` | 추적 텍스트 점검 결과·범위 기록, 원문 연락처 복제 없음 | 계정 노출 남음 |

## 검증 명령

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleRelease
node scripts/web/verify.cjs
node scripts/web/verify-map.cjs
python3 -m unittest discover -s scripts/tests -v
sha256sum -c apk/SHA256SUMS
```

웹 검사는 `scripts/serve-web.py`의 Range 지원 서버에서 생성된 `build/site`를 사용했습니다.
단순 HTTP 서버의 영상 탐색 실패는 Range 지원 서버로 재실행해 통과했습니다. 테스트 조건을 제거하지 않았습니다.
첫 전체 검사에서 lint Kotlin 분석기 내부 예외가 발생했습니다. 데몬 재시작 후 lint 오류 0개·기존 경고 4개였습니다.
실제 SDK 없는 JVM 환경에서 Robolectric이 null 서비스 연결을 전달하는 경쟁 조건은 테스트용 실패 bind로 고정했습니다.
성공 차량 서비스나 SDK 값을 위조한 것이 아니며 기존 테스트를 삭제·완화하지 않았습니다.

## 구현 범위와 남은 위험

- Console 제어 URL의 공식 문서 근거가 확인되지 않아 HTTP 제어를 자동화하지 않았습니다. 공식 Console UI 수동 조작 후 **실제 로그**로 단계별 판정합니다.
- Tire.getLowTirePressure의 실제 반환은 Float입니다. Boolean 저압 플래그를 만들지 않았고, 표시등 capability가 PRIVATE_CAR_SAFETY를 요구하므로 TPMS 표시등도 사용하지 않습니다.
- 운전석 좌우 식별 API를 확인하지 못해 앞좌석 한 개만 점유한 경우 동승자로 단정하지 않습니다. 벨트만으로 좌석 점유를 추정하지 않습니다.
- SDK 포함 lint에서 Safety.checkTelltalesCapabilities의 PRIVATE_CAR_SAFETY 요구를 재현했습니다. 비공개 권한을 요청하지 않고 해당 기능을 제외했습니다. 공개 CAR_SAFETY로 가능한 워셔액 읽기만 유지합니다.
- 새 실내 정보의 실제 권한·영역·응답, 분할 창, SOC 목표 변화·실제 목적지 ETA는 공식 기기에서 추가 검증해야 합니다.
- 속도 수신 시각 대안은 꺼져 있습니다. 실제 로그에서 타임스탬프 정지와 계속된 poll/콜백이 입증된 뒤에만 검토합니다.
- 블라인드 계정·커밋 작성자 노출과 팀 키 서명·동일 APK 새 영상·Release 게시는 사람이 완료해야 합니다.

## 후속 출시 점검

권한 재생성·영구 거부·서머타임 시각 오류를 추가 재현하고 수정했습니다. 최신 전체 검사는 프로필별 278개 통과입니다. [추가 점검 보고](RELEASE_AUDIT_0.11.1_KO.md)를 참고하세요.
