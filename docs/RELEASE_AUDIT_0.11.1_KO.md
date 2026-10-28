# 추가 출시 점검 — 0.11.1 소스

기존 수정의 push·CI 성공 이후 권한 수명주기와 전 세계 시간대의 도착 시각 선택을 추가 점검했습니다.
실제 Pleos 차량 시연을 대체하는 검증은 아닙니다.

## 재현 후 수정

| 결함 | 변경 파일 | 추가 회귀 테스트 | 수정 전 → 수정 후 |
|---|---|---|---|
| 권한 창 표시 중 Activity 재생성 후 허용해도 차량 재연결 누락 | CabinApp.kt, AutomotiveApplicationTest.kt | grantedPermissionAfterRecreationReconnectsAndKeepsSafetyLocked | 실패 → 통과 |
| 영구 거부 후 권한 버튼으로 허용 화면에 접근 불가 | CabinApp.kt, AutomotiveApplicationTest.kt, README.md | permanentlyDeniedPermissionButtonOpensAppSettings | 실패 → 통과 |
| 서머타임의 누락·반복 시각 때문에 다음 도착 날짜/시간 선택 오류 | ArrivalClockControls.kt, ArrivalClockTest.kt | skippedSpringClockTimeDoesNotShiftTomorrowsTargetHour, repeatedAutumnClockTimeUsesTheNextFutureOccurrence | 2개 실패 → 2개 통과 |

권한 요청 전 상태를 저장 가능한 Compose 상태로 보존합니다. 영구 거부 시 **사용자 버튼 클릭으로만** 앱 설정을 엽니다.
자동 요청은 기존처럼 최초 한 번이며, 권한 허용 자체가 정차 기능을 열지는 않습니다.
권한 테스트는 ActivityResultRegistry에 실제 계약의 결과를 전달하며 콜백 등록 여부도 확인합니다.
일반 거부는 rationale=true, 영구 거부는 false로 구분합니다.

도착 시각은 시간대의 유효 offset을 확인해 가장 가까운 미래 시각을 선택합니다.
존재하지 않는 시각은 다음 날짜의 해당 시각을 찾고, 두 번 존재하는 시각은 아직 지나지 않은 쪽을 선택합니다.

## 검증

```bash
./gradlew :app:testDebugUnitTest --tests '*ArrivalClockTest'
./gradlew :app:testDebugUnitTest --tests '*AutomotiveApplicationTest' --tests '*VehiclePermissionResultTest'
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
sha256sum -c apk/SHA256SUMS
```

집중 검사: 도착 시각 4개, 차량 UI·권한 결과 9개 통과. 기존 테스트를 삭제·무시·완화하지 않았습니다.
기본·공식 Pleos SDK 프로필 각각 core 144 + Android 134 = **278개 통과, 실패 0개, 제외 0개**입니다.
두 프로필 모두 release 빌드·lint 성공, lint 오류 0개와 기존 경고 4개입니다.
제출 APK 체크섬 일치 및 origin/main 대비 apk/ 변경 없음도 확인했습니다.
이전 단계의 웹 67개·Python 16개 통과 결과는 해당 코드가 이번 추가 수정에서 바뀌지 않아 유지하며 재실행으로 표기하지 않습니다.

## 남은 출시 조건

- 새 소스의 실제 Pleos 권한 승인·설정 복귀·회전·분할 창 및 전체 차량 시나리오 확인.
- 팀 키로 SDK 포함 0.11.1 서명, 동일 APK의 연속 영상·해시 검증, 제출 APK 교체와 Release 게시.
- 제출용 익명 계정/저장소 준비 및 블라인드 요건 확인. 개발 저장소 계정과 이력은 그대로 남아 있습니다.
- 기존 APK는 0.11.0이며 이번 소스 변경을 포함하지 않습니다. 기본 안전 판정과 DO 미선언은 유지합니다.
