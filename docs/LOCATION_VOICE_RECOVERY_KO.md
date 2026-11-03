# 위치·음성 초기화 복구 점검

2026-10-27 · 퓨처랩스

## 재현한 결함

1. 주변 검색 위치 요청이 `requestSingleUpdate`여서 첫 GPS가 500m 오차이면 거절한 뒤 구독이 끝났습니다. 이후 정확한 위치를 받을 수 없어 25초 후 실패했습니다. 이제 제한 시간 동안 1초 주기의 위치 갱신을 요청하고 유효한 위치에서 종료합니다. 10초 이내·오차 50m 이하 기준은 유지합니다.
2. TTS 초기화에 실패해도 기본 엔진 이름이 같으면 복귀 시 재연결하지 않았습니다. 명시적인 초기화 실패에 대해서만 재연결합니다. 준비 중이거나 정상인 엔진을 중복 생성하지 않습니다.
3. 목소리 선택 창이 초기화 실패도 계속 준비 중으로 표시했습니다. 실제 상태와 `음성 엔진 다시 연결` 버튼을 표시합니다. 재연결 성공 후 자동 선택이 활성화되고 재연결 버튼은 사라집니다.

## 재현과 검증

수정 전 대상 테스트 16개 중 다음 3개 실패를 확인했습니다.

- `NearbyLocationRecoveryTest.inaccurateFirstFixDoesNotConsumeTheOnlyLocationRequest`
- `SpeechOutputContinuityTest.failedEngineInitializationCanReconnectWithoutChangingTheDefaultEngine`
- `VoiceComfortSettingsTest.initializationFailureIsShownInsteadOfAnEndlessPreparingMessage`

수정 후 16개 통과를 확인했고, 별도로 `locationTimeoutRemovesSubscriptionsWithoutRelaxingAccuracy`를 추가했습니다. `cancellingNearbyLocationRemovesItsSubscriptions`는 취소 시 정리, 정확한 위치 재수신 테스트는 성공 시 정리를 검사합니다.

검증은 Robolectric 위치 제공자·TTS 콜백·Compose 화면 조작에 기반합니다. 실제 S20+ GPS 수신 품질이나 모든 지도 서비스의 가용성을 보증하는 결과가 아닙니다. 차량 안전 잠금 기준, 내비 경로 계산 서버, 실제 매장 데이터는 변경하지 않았습니다.

## 전체 검증 결과

`./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug :app:assemblePhonePreview` 통과. core 187개 + Android 282개, 총 469개 통과·실패/오류/건너뜀 0개입니다. 기존 검토본과 서명 일치 및 APK 정렬도 확인했습니다. [새 APK 검증 기록](../preview/location-voice-recovery-verification.json). 제출용 `apk/`는 변경하지 않았습니다.
