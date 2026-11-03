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


## 내비게이션·핵심 기능 재실행

차량용 앱은 정차 중 목적지를 Pleos 기본 내비로 전달하고 길 안내는 기본 내비가 담당합니다. 자체 OSM 내비는 일반 Android/웹 데모입니다. 아래 결과를 공식 Pleos 실차 주행 성공으로 해석하지 않습니다.

- SDK 포함 전체 core·Android 검사 286개, lint 오류 0개(기존 경고 4개)로 재실행 통과했습니다.
- 브라우저 기능 검사 145개 통과: 정차 세션·할 일·시간표·안전 잠금, 지도 조작·검색·GPS 단절·재탐색·음성 제어·날짜 변경선·모바일 레이아웃·성능. 지도/위치 응답을 주입한 제어 검사는 실서버 검사와 구분합니다.
- 실제 Android Compose/osmdroid 지도 검사 1개 추가 통과: Robolectric native graphics에서 실제 OSM 타일 12/12개, 실제 OSRM 경로 102개 좌표가 렌더링됐습니다. [확인한 화면](screenshots/navigation-live-recheck.png). GPS는 공개 장소 시험 좌표이며 물리 기기·실차 검증은 아닙니다.
- 실제 HTTPS 서버 요청에서 서울·도쿄·파리·뉴욕 지명 검색 HTTP 200, 자동차 경로 응답 Ok를 확인했습니다. 서버가 응답한다는 증거이며 도로별 정확도·실시간 교통·전 세계 경로를 보증하지 않습니다.
- 가게명 '스타벅스 원주무실'은 0개, 띄어쓰기 재검색 '스타벅스 원주 무실'은 후보 2개였습니다. 두 후보를 동일한 특정 지점이라고 단정하지 않습니다. OSM 미등록 가게·주소는 누락될 수 있습니다.
- 100,000개 좌표·GPS 120회·CPU 4배 지연 조건의 브라우저 표시 p95는 약 10.9ms, 최대 24.2ms였습니다. 초기 해석/표시는 약 601ms. 타일 요청을 차단한 합성 부하 검사이며 실제 차량 FPS·실서버 지연 수치가 아닙니다. 확대·축소·끌기·두 손가락 확대축소도 통과했습니다.

발견해 고친 문제: 브라우저 네트워크 오류가 'Failed to fetch' 그대로 노출됐습니다. 실패를 재현한 뒤 네트워크·인증서·사이트 접근 설정과 재시도를 안내하도록 수정했고, 실패 후 검색 버튼 복구 및 새 검색 성공을 확인했습니다. 터치 성능 검사에서는 접힌 부가 데모 메뉴를 여는 조작이 빠져 있어 테스트 순서를 갱신했습니다. 검증 조건은 삭제하지 않았습니다.

실제 브라우저 서버 연결은 환경 프록시 CA를 신뢰하지 않아 실패했습니다. CA 등록은 명시적 승인 없는 신뢰 범위 변경이라는 사유로 자동 승인 검토에서 거절됐으며, TLS 검증을 끄거나 우회하지 않았습니다. 대신 기존 인증서 검증을 유지하는 HTTPS 요청과 실제 Android 지도 경로를 확인했습니다.

처음 식별 정보 없는 지도 타일 검사에서는 PNG 형식의 접근 차단 안내를 받았습니다. PNG 디코딩만으로 도로 표시 성공이라고 판단하지 않았고, 앱의 실제 User-Agent를 사용한 동일 서울 타일을 다시 요청해 도로·지명·건물을 이미지로 확인했습니다. 공식 Pleos 기본 내비의 실제 목적지 전달·ETA, 기기 GPS·음성 엔진, 미등록 상호와 모든 국가의 도로 정확도는 여전히 미검증입니다.

## 내비게이션 예외 처리와 최신 APK 실행 범위 보완

- `LiveNavigationTest.languageChangeKeepsSearchCredentialUntilPersonalDataIsCleared`: 언어 변경 시 검색 캐시와 함께 자체 서버 접근키가 삭제되는 결함을 재현했습니다. 캐시 비우기와 개인정보 삭제를 분리했습니다. 언어 변경은 접근키를 유지하며, 개인정보 삭제는 접근키도 제거합니다. 테스트 문자열은 가짜 키입니다.
- `LiveNavigationTest.queuedGpsAfterPermissionRevocationCannotRestoreNavigationReadiness`: 권한 취소 직후 대기 중이던 위치 콜백이 수신 가능 상태를 다시 표시하는 결함을 재현했습니다. 콜백 진입 시 정확한 위치 권한을 확인하고, 상실 시 구독을 끊고 위치·준비 상태를 무효화합니다. 기존 세대의 콜백도 재사용되지 않습니다.
- 위 두 테스트는 수정 전 3개 중 2개 실패, 수정 후 3개 모두 통과했습니다. 차량의 P·정지 1.5초 및 신호 만료 기준은 변경하지 않았습니다.
- `ApplicationTest.activeGuidanceKeepsRerouteConnectionFailureVisible`: 안내 중 재탐색 연결 실패 사유와 경로 이탈 상태가 동시에 보이고 검색 조작은 숨겨지는지 확인했습니다. 기존 화면이 통과하므로 제품 변경 없이 검사만 추가했습니다.
- `./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` 통과: core 144 + Android 145 = **289개, 실패 0개, 제외 0개**, lint 오류 0개·기존 경고 4개. release는 unsigned 빌드 검증이며 팀 서명·배포 검증이 아닙니다.
- Python 녹화·패키징 검사 30개, 서버 검사 11개 통과했습니다.
- 기존 CI 에뮬레이터 검사는 `apk/`의 이전 제출 APK만 실행했습니다. 이를 유지하면서 `current-source-debug` 검사를 추가해 현재 커밋을 공식 SDK 포함 debug APK로 빌드·설치하고 실제 화면을 연속 녹화하도록 했습니다. 두 결과는 별도 artifact에 보관합니다. debug 실행은 최신 코드의 일반 Android 동작 검증이며 정식 서명·Pleos 서비스·실차 검증을 대신하지 않습니다. 실행 결과는 해당 커밋의 GitHub Actions에서 확인합니다.

이번 변경으로 `apk/` 제출 파일·체크섬은 갱신하지 않았습니다. 실제 기기 GPS·음성·Pleos 내비 전달과 팀 서명 후보의 전체 시연은 계속 별도 확인이 필요합니다.

최신 debug APK의 첫 CI 기기 실행과 새 runner 재실행은 모두 UIAutomator 조회 실패(exit 255), 이어진 logcat 45초 시간 초과로 실패했습니다. 기존 제출 APK 작업과 빌드·단위 검사는 통과했습니다. 이 환경에서는 Actions 로그·artifact 다운로드가 Forbidden이므로 상세 기기 로그/영상은 열람하지 못했습니다. 원인을 앱 또는 메모리 부족으로 단정하지 않습니다. 최신 기기 작업에서는 빌드 직후 최대 3GB Gradle daemon이 남을 수 있어 `--no-daemon` 및 Kotlin in-process 컴파일로 빌드 프로세스를 종료한 뒤 에뮬레이터를 시작하도록 보완했습니다. 결과는 후속 CI에서 확인하며 첫 실패를 성공으로 간주하지 않습니다.
