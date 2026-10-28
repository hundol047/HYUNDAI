# 1시간 추가 점검 — 위치·지도 조작·배경음·음성 명령

퓨처랩스 · CabinOS ONE · 2026-10-28 · `fix/vehicle-runtime-issues`

점검 시작: 18:07:24 KST. 최종 종료 시각과 전체 결과는 `hour-verification.json`에 기록합니다.
수정 전 실패를 재현한 테스트와 정상 동작을 보호하는 테스트를 추가했습니다. 기존 테스트 삭제·완화, main 변경, 제출용 `apk/` 교체는 하지 않았습니다.

## 수정 내용

| 유형 | 실제 문제와 수정 | 주요 파일 | 추가 테스트 |
|---|---|---|---|
| 공통 위치 수신 | 사용하지 않는 제공자 종료가 정상 GPS까지 지웠습니다. 잘못된 좌표·오래된 좌표가 위치 사용 가능 상태를 만들고, 후속 수신이 없으면 그 상태가 계속됐습니다. 제공자별 처리를 구분하고 10초 유효기간 타이머·좌표 검증·대략적 위치 권한에 맞는 구독을 적용했습니다. 정차 중 갱신도 받도록 최소 이동 거리는 0m입니다. | `Providers.kt` | `LocationLifecycleTest`: 제공자 구분, 자동 만료, 무효/과거 좌표, 대략적 권한, 새 위치의 만료 타이머 보존 5개 |
| 지연 배경음 | 로딩 중 일시정지·재개하거나 다른 세션을 시작해도 이전 소리가 뒤늦게 켜졌습니다. 요청을 세션 ID에 묶고 중지 시 무효화합니다. 화면 회전 시 유지되는 ViewModel의 정상 재생은 변경하지 않습니다. | `AmbientAudio.kt`, `CabinViewModel.kt` | `PendingAudioTest.lateAudioCannotRestartAfterPauseAndResume`, `lateAudioCannotCrossIntoANewSession` |
| 지도 터치 | 1픽셀 탭 떨림이 자동 추적을 해제하고 드래그마다 콜백을 반복했습니다. Android 터치 허용 오차를 넘는 드래그 또는 두 손가락 조작에서 한 번만 해제합니다. | `RoadMapView.kt` | `RoadMapGestureTest` 3개: 작은 떨림, 드래그/취소/다음 탭, 두 손가락 확대 및 조작 차단 |
| 소리 끄기 음성 | “빗소리 꺼줘”가 소리 이름에 먼저 매칭되어 재생 요청으로 처리됐습니다. 끄기 동작을 소리 선택보다 먼저 처리합니다. | `CabinViewModel.kt` | `PendingAudioTest.namedSoundStopCommandNeverStartsPlayback` |
| 음성 세션 시작 | 버튼으로 집중·휴식을 시작하면 외출 계획을 해제하지만 음성은 유지해 불필요하게 세션 시간이 짧아졌습니다. 음성도 차내 활동 시작과 동일하게 처리합니다. | `UnifiedVehicleContextEngine.kt` | `ContextEngineTest.voiceCabinSessionClearsPreviousOutdoorPlanLikeStartButton` |
| 시계 보정 후 검색 | 시계가 뒤로 보정되면 다음 검색·경로 요청이 시차만큼 장시간 대기할 수 있었습니다. 검색/차량 경로는 최대 1.5초, 보행은 최대 1.2초의 요청 간격만 기다립니다. 정상 요청 간격은 유지합니다. | `RequestTiming.kt`, `NavigationService.kt`, `WalkingService.kt` | `RequestTimingTest.clockCorrectionDoesNotStrandNextPlaceSearch`, `immediateSearchesStillRespectProviderInterval` |
| GPS 점프의 지도 반영 | 경로 엔진이 거부한 위치 점프도 현재 지도 좌표와 위험 관측으로 전달됐습니다. 거부 시 현재 위치를 해제하고 마지막 정상 경로 위치를 비교 기준으로 보존합니다. 동일 시각의 정상 보정 좌표도 복구할 수 있습니다. | `LiveNavigation.kt` | `NavigationDetailTest.rejectedGpsJumpCannotBecomeCurrentMapPositionAndSameTimeCorrectionRecovers` |
| 함께 휴식 버튼 | 활동 가능 시간이 1~59초여도 버튼은 켜져 있지만 엔진은 시작을 거절했습니다. 다른 세션 시작 버튼처럼 최소 60초 기준을 적용하고 시간 부족 이유를 표시합니다. | `CabinAdvicePanel.kt` | `ParkedUxTest.sharedRelaxActionExplainsInsufficientTimeAndEnablesAtOneMinute` |

새 테스트 16개입니다. 결함 재현과 정상 경로 보호를 함께 포함하며, 16개의 별개 제품 결함으로 세지 않습니다. 안전한 주차 판정(P/유효 속도 0, 1.5초)과 차량 신호 만료 기준은 유지합니다.

## 실행 검증

```sh
source scripts/env.sh
./gradlew --no-daemon --max-workers=1 -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assemblePhonePreview --console=plain
python3 -m unittest discover -s server -v
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-navigation-live.py --all-regions --output /tmp/hour-live-navigation.json
```

최종 전체 테스트는 core 190개 + Android 326개 = 516개이며 실패·오류·skip 0개입니다. 서버 25개와 도구 60개도 통과했습니다. SDK 포함 빌드·lint의 최종 결과는 [검증 기록](hour-verification.json)에 기록합니다. SDK 없는 빌드로 대체하지 않습니다.

첫 전체 실행에서 새 위치 만료 테스트의 타이머 시계와 wall clock이 따로 진행되어 1개가 실패했습니다(Android 325개 중 1개). 테스트 시계를 명시적으로 주입해 실제로 서로 다른 수신 시각을 검증하도록 고쳤습니다. 만료 시간이나 검증 조건을 완화하지 않았습니다.

실제 외부 조회: 서울·파리·뉴욕·도쿄·상파울루·케이프타운·시드니 검색/자동차 경로와 OSM 타일, 총 15건의 HTTP 검사를 통과했습니다. [응답 검증](hour-live-navigation.json). Railway 공개 `/healthz`·`/v1/dataset`도 HTTP 200이며 과거 사고 통계 1,271개 구역을 반환했습니다. [서버 확인](hour-echo-health.json). 접근키나 개인 GPS는 이 검사에 사용하지 않았습니다. 판교의 공개 시험 좌표에서 Photon 주변 검색도 실제로 요청했고 카페·식당·산책·쇼핑 각 15개의 이름 있는 장소를 받았습니다. [제한된 주변 검색 표본](hour-nearby-live.json). 다른 지역의 완전한 수록이나 현장 영업 여부를 뜻하지 않습니다.

## 검증의 한계

일반 Android 에뮬레이터 CI, Robolectric UI/위치 콜백 테스트, 실제 공개 서버 응답은 각각 구분합니다. 실제 S20+ 수신 환경·차량 내 오디오·공식 Pleos 차량 이미지에서 이 APK를 실행한 결과가 아닙니다. 공공 지도/검색의 모든 매장·도로, 실시간 교통, 상용 내비와 같은 정확도를 보장하지 않습니다. 자연스러운 음색은 기기의 설치된 TTS 엔진·오프라인 음성팩에 따릅니다.

휴대폰 검토본은 기존 테스트 서명을 유지합니다. 팀 서명 키가 없어 제출용 APK는 교체하지 않습니다.

최종 공식 SDK 포함 release(미서명)·휴대폰 검토본(기존 테스트 서명) 빌드가 통과했습니다. lint 오류 0개·기존 경고 14개입니다. [수정 소스 CI](https://github.com/hundol047/HYUNDAI/actions/runs/40667617550)도 통과했습니다.

[수정 소스의 일반 Android 에뮬레이터 CI](https://github.com/hundol047/HYUNDAI/actions/runs/40667609303)에서 debug 및 테스트 키로 서명한 release 실행 검사가 통과했습니다. 휴대폰 검토본의 패키지/바이너리 자체와는 구분합니다.

실제 타일 테스트의 첫 실행은 EchoRoad 12/12 타일 표시를 통과했으나 내비 화면은 시간 초과였습니다. 자동 확대 지도가 Dialog의 별도 창으로 이동하는데 테스트가 Activity 기본 창만 검색하던 문제를 확인하여, 모든 실제 창에서 지도 창을 찾도록 검사 코드를 수정했습니다. 준비 상태·전체 타일 수 검증과 35초 제한은 그대로 유지하고 자동 확대 창인지 확인하는 검증도 추가했습니다. 앱 동작이나 다운로드 결과를 대체하지 않습니다.

수정된 실제 지도 테스트 2개 모두 통과했습니다. 내비 확대 창은 가시 타일 16/16개·실제 경로 점 102개, EchoRoad는 12/12개·zoom 17입니다. [지도 검증 JSON](hour-map-render.json), [확대 내비 화면](hour-navigation-roads.png), [EchoRoad 화면](hour-echoroad-roads.png). Android Compose/osmdroid를 Robolectric native graphics로 렌더링했으며 실기기 운전 영상이 아닙니다.

공개 검토용 APK를 다시 다운로드해 SHA-256을 대조했고 QR도 해당 불변 커밋의 APK 주소로 해독됨을 확인했습니다. 최종 점검 종료 시각은 검증 JSON의 `finishedAt`에 기록했습니다.
