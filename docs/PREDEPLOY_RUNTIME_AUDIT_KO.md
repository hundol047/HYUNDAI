# 배포 전 수명주기·저장소 점검

0.11.1 소스의 추가 점검입니다. 기존 서명 APK(0.11.0)를 변경하거나 새 버전을 배포하지 않았습니다.

## 재현·수정

| 문제 | 변경 파일 | 추가 테스트 | 수정 전 → 후 |
|---|---|---|---|
| 차량 transport 종료 예외가 신호 초기화를 건너뛰며, 초기화 도중 실패한 transport가 해제되지 않음 | VehicleStateProvider.kt | teardownFailureStillImmediatelyInvalidatesSignalsAndAllowsReconnect, partiallyStartedTransportIsReleasedAndLateCallbacksStayInvalid | 2개 실패 → 통과 |
| 구독 중지·재연결·권한 상실 후 늦은 GPS 콜백이 위치 상태를 복구함 | Providers.kt, LocationLifecycleTest.kt | stoppedAndPreviousSubscriptionsCannotRestoreLocationAvailability, revokedPermissionRejectsQueuedLocationAndClearsPreviousFix | 2개 실패 → 통과 |
| 저장된 계획이나 할 일 읽기 예외가 처리되지 않아 초기화·구독이 중단됨 | CabinViewModel.kt, CabinApp.kt, StorageRecoveryTest.kt | unreadableCheckpointDoesNotCrashOrOverwriteSavedDataAndVehicleMonitoringContinues, taskReadFailureIsReportedAndRecoversWithoutRestartingTheApp | 2개 실패 → 통과 |

- 차량 연결 해제는 신호 무효화를 먼저 수행합니다. 종료 실패가 있어도 과거 콜백을 거부하고 새 연결에는 새 신호를 요구합니다. 부분 초기화 실패도 리소스를 해제합니다.
- GPS 구독마다 세대를 구분합니다. 중지 시 위치를 지우고, 권한을 잃은 콜백도 위치 상태를 복구할 수 없습니다.
- 초기 저장 데이터 읽기에 실패하면 자동 저장과 할 일 변경을 중지합니다. 기존 계획을 기본값으로 덮어쓰지 않습니다. 실제 차량 상태 확인은 계속하며 정차 안전 조건은 그대로 적용합니다.
- 할 일 읽기의 일시적인 오류는 5초 후 재시도합니다. 복구된 목록을 표시하며 사용자에게 저장소 상태를 안내합니다. 앱을 다시 시작하거나 사용자가 명시적으로 개인 데이터를 삭제한 뒤에만 초기 복원 실패 상태에서 저장을 재개합니다.

## 검증 명령

```bash
./gradlew :app:testDebugUnitTest --tests '*VehicleStateProviderTest' --tests '*LocationLifecycleTest'
./gradlew :app:testDebugUnitTest --tests '*StorageRecoveryTest'
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
sha256sum -c apk/SHA256SUMS
```

집중 검사: 차량·위치 8개, 저장소 복구 2개 통과. 예외·지연 콜백을 테스트에서 주입한 결과이며 실제 OEM 장애를 재현한 영상은 아닙니다.

전체 검사는 기본·공식 SDK 포함 프로필 각각 **core 144 + Android 140 = 284개 통과, 실패 0개, 제외 0개**입니다.
두 프로필 모두 release 빌드·lint 성공(오류 0개, 기존 경고 4개). 제출 APK 체크섬 일치, origin/main 대비 apk/ 변경 없음도 확인했습니다.
웹·Python 코드는 이번 변경 대상이 아니며 이전 통과 결과를 새 실행 결과로 표기하지 않습니다.

## 실제 출시 전에 남은 검증

- 공식 Pleos에서 권한 상실·서비스 재연결·백그라운드 복귀·분할 창·SOC 및 ETA 변경 전체 흐름.
- 팀 키로 새 release 서명 후 같은 APK의 연속 시연과 SHA-256 대조.
- 실제 저장소 고장·용량 부족 및 OEM별 위치 서비스 정책. 손상된 데이터베이스를 자동 삭제하거나 복구 성공으로 위장하지 않습니다.
- 블라인드 제출용 계정/저장소 준비. main 병합과 APK 교체·Release 게시를 수행하지 않았습니다.


## 최종 재점검: 삭제 실패와 배포 파일

- `failedDeletionStopsAutomaticWritesAndRetryCanComplete`: 삭제 실패 후 자동 저장으로 기존 데이터를 바꾸는 문제를 재현했습니다. 삭제 시작부터 자동 저장을 중지하고 성공한 경우에만 재개합니다. 실패 상태는 재시도 또는 재시작 전까지 유지합니다. 저장소 삭제가 실패한 경우 기존 데이터가 남을 수 있으며 성공 메시지를 표시하지 않습니다.
- `taskRecoveryCannotHideAnUnrecoveredPlanReadFailure`: 할 일 구독이 회복되면 미해결 계획 복원 오류까지 사라지는 문제를 재현했습니다. 복원·할 일 읽기·쓰기 안내를 분리하고 복원 실패를 우선 표시합니다.
- 두 회귀 테스트는 수정 전 실패, 수정 후 통과했습니다. 기존 저장소 테스트 2개도 유지했습니다.
- `scripts/verify-release-candidate.py`: APK 버전·패키지·debug 여부·SDK 빌드 표시·암호학적 서명·팀 인증서·체크섬을 검사합니다. 실패 시 종료 코드 1이며 APK 수정·서명·게시 기능은 없습니다. `test_release_candidate.py`의 8개 검사 케이스를 추가했습니다.
- 현재 0.11.0 APK를 실제 검사하여 버전 불일치·새 SDK 빌드 표시 부재로 거부됨을 확인했습니다. 이 거부 검증에서는 기존 APK의 인증서 지문을 사용했으며, 이를 팀 키 신뢰 검증으로 주장하지 않습니다. 기존 APK가 SDK 미포함이라는 판정도 아닙니다.
- 새 검사 도구는 실제 Pleos 시연을 대체하지 않습니다. 팀 키로 서명한 새 APK와 동일 파일 시연은 여전히 배포 선행 조건입니다.

최종 재점검 결과: 기본·공식 SDK 포함 프로필 각각 **core 144 + Android 142 = 286개 통과, 실패 0개, 제외 0개**, release 빌드·lint 성공(오류 0개, 기존 경고 4개). Python 검사 **24개 통과**. 실제 생성 APK 두 프로필의 SDK 표시 false/true도 확인했습니다. SDK 포함 새 APK는 아직 unsigned 산출물이며 배포 가능한 서명본으로 간주하지 않습니다.
