# 상태 전환 추가 점검

퓨처랩스 · CabinOS ONE · 2026-10-28 · `fix/vehicle-runtime-issues`

앞선 8개 수정에 이어, 권한 해제·위치 제공자 변경·주차 신호 손실·늦은 서버 응답을 검사했습니다. 추가로 4가지 유형의 결함을 수정했습니다.

## 1. 주차 제한이 걸린 뒤 이전 요청이 살아나는 문제

GPS나 서버 응답을 기다리는 동안 차량 신호가 주행/미확인으로 바뀌었다가 다시 P/0으로 확인되면, 이전 요청이 늦게 완료되어 안내를 시작하거나 검색 결과를 덮어쓸 수 있었습니다. 응답 시점의 `canPlan()` 검사만으로는 그 사이의 상태 변화를 막지 못했습니다.

`LiveNavigation`에서 안전한 주차 상태 상실을 관찰해 대기 목적지·자동 시작 의도를 폐기하고 요청 세대를 갱신합니다. 취소를 무시하고 늦게 응답하는 서버도 결과를 반영하지 못합니다. 이미 시작된 안내의 GPS 추적·재탐색은 이 취소 대상과 구분합니다. P/0 1.5초 및 차량 신호 만료 기준은 변경하지 않았습니다.

추가 테스트:
- `NavigationDetailTest.safetyLockCancelsGpsPendingDestinationEvenAfterParkingAgain`
- `NavigationDetailTest.safetyLockRejectsLateRouteAfterParkingAgain`
- `NavigationDetailTest.safetyLockRejectsLateSearchAfterParkingAgain`

## 2. Android 10의 위치 상태 콜백에서 앱 종료

API 29에서는 `LocationListener.onProviderEnabled`, `onProviderDisabled`, `onStatusChanged`가 추상 메서드입니다. 최신 SDK의 기본 구현을 기대하고 일부 메서드를 생략했던 세 위치 수신 경로에서 `AbstractMethodError`를 재현했습니다.

`AndroidLocationProvider`, `LiveNavigation`, `NearbyPlaces`에서 필요한 콜백을 명시적으로 구현했습니다. 제공자가 켜졌다는 이벤트를 실제 위치 수신으로 오인하지 않습니다.

추가 테스트:
- `LocationLifecycleTest.legacyProviderCallbacksDoNotCrashOrInventAvailability`
- `NavigationDetailTest.legacyProviderCallbacksDoNotCrashOrInventAFix`
- `NearbyLocationRecoveryTest.legacyProviderCallbacksKeepWaitingForActualLocation`
- 아래의 위치 서비스 해제 테스트도 API 29 종료 경로를 검증합니다.

## 3. 주변 검색의 위치 권한·서비스 해제 처리

정확한 위치 요청 뒤 권한을 해제하거나 대략적 위치로 낮춰도, 큐에 남아 있던 정밀 위치가 그대로 채택됐습니다. 콜백 수신 시 권한을 다시 확인하여 해당 요청을 종료하고 구독을 제거합니다. 이후 사용자가 대략적 위치 권한으로 새 검색을 요청하는 동작은 유지합니다.

위치 서비스가 모두 꺼진 경우에도 25초 제한 시간 전체를 기다리지 않도록 즉시 종료합니다. 네트워크 위치만 꺼지고 GPS가 살아 있는 경우에는 계속 GPS를 기다립니다. 기존 정확도·위치 나이 기준은 유지합니다.

추가 테스트:
- `NearbyLocationRecoveryTest.queuedPreciseLocationIsRejectedAfterPermissionRevocation`
- `NearbyLocationRecoveryTest.preciseRequestStopsAfterPermissionDowngrade`
- `NearbyLocationRecoveryTest.disablingEveryProviderEndsNearbyWaitImmediately`
- `NearbyLocationRecoveryTest.disablingUnusedNetworkProviderStillWaitsForGps`

## 4. 주행 중 매 상태 갱신마다 반복되는 알림 취소

출발 알림이 이미 취소된 상태에서도 차량 상태 갱신마다 OS 알람 취소·알림 취소·SharedPreferences 동기 쓰기를 반복했습니다. 같은 인스턴스에서 완료한 취소를 기억해 중복 작업을 생략하고, 새 계획 예약·유효 알림 수신 때는 다시 취소할 수 있도록 초기화합니다. 저장된 새 토큰이 있으면 생략하지 않습니다.

30회 연속 주행 갱신에서 알람 취소 호출은 30회에서 1회로 줄었습니다. 새 계획을 예약하거나 Receiver가 알림을 게시한 뒤 주행하는 경우에도 취소가 동작함을 확인합니다. 실제 프레임 속도 개선 수치를 측정한 것은 아닙니다.

추가 테스트:
- `ReminderCancellationTest.repeatedDrivingUpdatesCancelTheSystemAlarmOnceButANewPlanCanBeCancelled`
- `ReminderCancellationTest.newPlanDeliveredByReceiverCanStillBeClearedWhenDriving`

## 검증

신규 테스트 12개: 수정 전 결함을 재현한 10개와 정상 경로 보호 2개입니다. 수정 전 실패는 5개(전환·권한·제공자 해제), 4개(구형 콜백·권한 축소), 1개(중복 알람 취소)입니다. 수정 후 관련 테스트 71개가 통과했습니다.

신규 알람 호출 계수 테스트는 예약 시 ShadowAlarmManager 내부의 기존 알람 교체 호출과 주행 시 취소를 분리해 계수했습니다. 기존 테스트의 조건이나 제한 시간은 완화하지 않았습니다.

전체 검사 및 빌드 결과: [transitions-verification.json](transitions-verification.json).

```sh
source scripts/env.sh
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assemblePhonePreview --console=plain
```

실제 S20+·공식 Pleos 차량 에뮬레이터에서 이번 APK를 실행한 결과로 표현하지 않습니다. 일반 에뮬레이터 CI도 별도 결과로 구분합니다. main과 제출용 `apk/`는 변경하지 않습니다.

전체 회귀 결과는 core 189개 + Android 311개 = 500개, 실패·오류·skip 0개입니다.

첫 전체 명령은 테스트를 통과한 뒤 lint의 Kotlin FIR 분석기 내부 오류로 실패했습니다(`ApplicationTest.kt`, `RAW_FIR`→`TYPES`). 검사나 테스트를 제외하지 않고 아래처럼 새 Gradle 프로세스·단일 worker로 재실행했습니다. lint 오류 0개, 기존 경고 14개입니다.

```sh
./gradlew --no-daemon --max-workers=1 -PpleosSdk=true :app:lintDebug :app:assembleRelease :app:assemblePhonePreview --console=plain
```

[수정 소스 CI](https://github.com/hundol047/HYUNDAI/actions/runs/40660122745) 최종 통과. 같은 소스의 debug·테스트 서명 release APK를 일반 Android 에뮬레이터에서 실행한 CI 검사도 통과했습니다. 공개 검토용 APK의 실제 S20+ 검사와 동일한 검증은 아닙니다.

최종 SDK 포함 release(미서명)·휴대폰 검토본(기존 테스트 서명) 빌드가 통과했습니다. 공개 검토본은 팀 서명 제출 APK가 아니며 기존 검토본과 같은 서명으로 업데이트할 수 있습니다.
