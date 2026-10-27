# 목소리 선택 조작 개선

2026-10-27 · 퓨처랩스

## 재현한 문제와 수정

- 선택 창을 연 상태에서 안전 잠금이 걸리면 창만 숨겨지고 열림 상태는 남았습니다. 다시 정차하면 창이 저절로 나타났습니다. 이제 사용 가능 상태가 해제되면 열림 상태도 초기화합니다.
- 음성 엔진이 준비되기 전에 창을 열면 빈 목록이 유지됐습니다. 엔진 준비 상태를 화면에서 관찰해 준비 후 설치 목소리를 다시 읽고, 준비 전에는 자동 선택을 비활성화합니다.
- 목소리를 들어보던 중 완료·뒤로 가기·바깥 영역으로 창을 닫아도 음성이 이어졌습니다. 명시적으로 창을 닫으면 미리듣기도 중지합니다.

차량 속도·P 기어·신호 만료의 안전 판정은 변경하지 않았습니다. 앞선 [음성 오류 수정](VOICE_RUNTIME_REVIEW_KO.md)도 새 검토본에 포함됩니다.

## 회귀 테스트

`VoiceComfortSettingsTest`의 다음 테스트를 수정 전에 실행해 3개 모두 실패하는 것을 확인했습니다.

- `safetyLockDismissesChooserWithoutReopeningAfterParking`
- `engineReadyRefreshesAnAlreadyOpenVoiceList`
- `closingChooserStopsItsPreview`

수정 후 위 3개와 기존 음성 연속 재생 테스트 9개, 총 12개가 통과했습니다. 실제 화면 클릭·안전 상태 전환·TTS 준비 콜백을 Robolectric으로 검사한 결과이며 실제 휴대폰 녹화는 아닙니다.

## 전체 검증

`./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug :app:assemblePhonePreview` 통과. core 187개 + Android 277개, 총 464개 통과·실패/오류/건너뜀 0개입니다. 기존 검토본과 서명 일치 및 APK 정렬도 확인했습니다. [새 APK 검증 기록](../preview/voice-ux-verification.json). 제출용 `apk/`는 변경하지 않았습니다.
