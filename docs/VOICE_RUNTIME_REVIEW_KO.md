# 음성 안내 재점검

2026-10-27 · 퓨처랩스

## 수정

1. TTS가 아직 준비되지 않았거나 요청을 거부해도 안내 중복 방지 캐시에 기록되어 같은 안내가 30초간 막히던 문제를 수정했습니다. 실제 요청이 접수되면 기존 거리 단계별 중복 억제를 유지합니다. 명시적 다시 듣기·다음 교차로·새 경로 처리는 유지합니다.
2. TTS의 동기 `ERROR` 반환이 `outputFailure`에 전달되지 않던 문제를 수정했습니다. 내비 화면에서 즉시 실패를 확인할 수 있으며, 다음 요청에서 언어·음성을 다시 설정합니다. 성공적으로 재시도하면 실패 상태를 지웁니다.
3. 목소리 설정의 남은 `~해요` 문구를 요청한 `~입니다`체로 정리했습니다.

## 재현 테스트

- `GuidanceRepeatGuardTest.rejectedSpeechCanRetryWithoutWaitingThirtySeconds`
- `SpeechOutputContinuityTest.immediatePlaybackRejectionIsVisibleAndRetryClearsFailure`

수정 전 대상 테스트 12개 중 위 2개 실패를 확인했습니다. 기존 테스트를 삭제하거나 건너뛰지 않았습니다.

검증은 코드·자동화 테스트 범위입니다. 실제 삼성 휴대폰의 스피커·마이크·도로 주행이나 모든 지도 데이터의 정확성을 확인한 것은 아닙니다. 차량의 속도·P 기어·신호 만료 잠금 기준은 변경하지 않았습니다.

## 수정 후 결과

`./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug` 통과. core 187개, Android 274개, 실패·오류·건너뜀 0개입니다. 다운로드용 APK와 기존 영상은 이번 점검에서 교체하지 않았습니다.
