# CabinOS ONE 추가 결함 점검 — 2026-10-28

퓨처랩스 · `fix/vehicle-runtime-issues`. 20개 영역을 검토했으며, 새로 재현한 문제는 8개입니다. 문제 수를 맞추기 위한 변경은 하지 않았습니다. 아래의 ‘추가 결함 미발견’은 코드와 자동 테스트 범위의 결과이며 모든 기기·실제 도로에 대한 무결함 보장이 아닙니다.

## 재현 후 수정한 8개 문제

| 번호 | 문제와 변경 | 추가 회귀 테스트 |
|---|---|---|
| 1 | GPS 대기 목적지가 지역 검색·서버/언어 변경·백그라운드 전환 뒤 남아 늦게 안내를 시작함. 이전 자동 시작 의도를 폐기하고, 복귀 시 다시 목적지를 선택하도록 안내 | `NavigationDetailTest.changingPlanningContextCancelsDestinationWaitingForGps` (4가지 전환) |
| 2 | GPS 다시 연결 시 여전히 유효한 동일 시각의 프레임워크 위치를 중복으로 거부함. 명시적 재연결 대기에서만 동일 시각 수신 허용. 정확도·10초 만료·역순 거부 유지 | `NavigationDetailTest.reconnectCanReuseTheSameStillFreshFrameworkFix` |
| 3 | 서버가 필요 없는 위도/경도 입력도 오프라인에서 막히고 화면 검색어가 갱신되지 않음. 좌표는 로컬 해석, 경로 계산은 기존 온라인 조건 유지 | `NavigationDetailTest.offlineCoordinatesAreLocalAndUpdateTheVisibleQuery` |
| 4 | 빈 검색어는 기존 결과를 지우고, 오프라인 장소 검색은 이유 없이 무반응. 기존 결과를 유지하며 각각 검색어·연결 안내 표시 | `NavigationDetailTest.blankAndOfflineTextSearchExplainWhyWithoutErasingResults` |
| 5 | GPS 대기 요청 취소 후에도 화면은 ‘위치를 허용하면 경로 계산’ 상태로 남음. 대기 목적지 취소 사실 표시 | `NavigationDetailTest.cancellingGpsWaitReplacesThePendingInstruction` |
| 6 | 선택한 장소의 보행 경로를 재계산해도 왕복 예약 시간이 이전 값으로 남음. 새 왕복 시간을 일정에 반영하고 진행 중 세션 마감도 단축 | `WalkingPlanTest.recalculatingSelectedWalkUpdatesReturnReserveAndSessionDeadline` |
| 7 | 주행 후 다음 정차에서 이전 주차 위치의 보행 추천을 다시 선택할 수 있음. 실제 DRIVING 확정 전환에서 이전 목록 폐기 | `WalkingPlanTest.drivingClearsRecommendationsFromThePreviousParkingLocation` |
| 8 | 기기 시각을 과거로 보정하면 30초 주변 검색 제한이 수십 분 이상으로 늘어남. 시각 역전 시 이전 제한을 적용하지 않음 | `NearbyQueryCacheTest.clockCorrectionCannotBlockNearbySearchForHours` |

수정 전: core 189개 중 신규 2개 실패, 관련 Android 테스트 47개 중 신규 6개 실패. 수정 후: 같은 core 189개 및 Android 47개 통과. 기존 테스트는 삭제·완화·무시하지 않았습니다.

## 20개 점검 영역

| 영역 | 결과 / 확인 근거 |
|---|---|
| 1. 지연 GPS와 이전 목적지 | 수정 1, 취소·전환 테스트 |
| 2. GPS 재연결 | 수정 2, 동일 시각 재사용 및 기존 역순·저정확도 테스트 |
| 3. 오프라인 좌표 목적지 | 수정 3, 네트워크 호출 없이 좌표 반환 |
| 4. 검색 실패/입력 피드백 | 수정 4, 결과 유지와 원인 문구 |
| 5. 대기 요청 취소 표시 | 수정 5, GPS 대기 취소 |
| 6. 비대칭 보행 시간과 집중 세션 | 수정 6, 왕복·복귀·세션 잔여 시간 확인 |
| 7. 정차 간 추천 데이터 수명 | 수정 7, 주행 후 재주차 |
| 8. 주변 검색 제한과 시각 보정 | 수정 8, 캐시 지역·만료·시각 역전 |
| 9. P/0 확인 및 차량 신호 만료 | 추가 결함 미발견: `ContextEngineTest`, `VehicleSignalGateTest`, `VehicleReceiptFreshnessTest` |
| 10. 차량 권한 거부·재연결 | 추가 결함 미발견: `VehiclePermissionResultTest`, `AutomotiveApplicationTest` |
| 11. Pleos capability 재확인·읽기 실패 | 추가 결함 미발견: `PleosVehicleReaderTest`; 30초 미지원 재확인, 3초 타임아웃 유지 |
| 12. 음성 안내 반복·지연 콜백 | 추가 결함 미발견: `SpeechOutputContinuityTest`, `NavigationSpeechProgressTest`, `GuidanceRepeatGuardTest` |
| 13. 마이크 취소·시간 초과 | 추가 결함 미발견: `SpeechRecognitionLifecycleTest`, `NavigationDetailTest` |
| 14. 한국어 및 지원 언어 문구·음색 선택 | 추가 결함 미발견: `NavigationSpeechTest`, `NavigationLanguageTest`, `NavigationSpeechVoiceTest`; 실제 음색은 기기 음성팩 의존 |
| 15. 회전·배경음·오디오 포커스 | 추가 결함 미발견: `RotationAudioTest`, `AmbientAudioTest`, `RecordedAmbienceTest`; 이번 점검에서 실제 청음은 수행하지 않음 |
| 16. 낮은 차량 창·수동 지도 이동 | 추가 결함 미발견: `NavigationSafetyUiTest`, `NavigationMapRendererTest` |
| 17. 장소 분류·사진의 실제 지점 일치 | 추가 결함 미발견: `NearbySearchCoordinatorTest`, `PlacePhotoReferenceTest`, `PlacePhotoResolverTest`; 사진 없는 지점의 실제 사진을 생성하지 않음 |
| 18. 교통 정보 만료·네트워크 차단 | 추가 결함 미발견: `TrafficLifecycleTest`, `TrafficFlowTest`; 국내 교통 연결 제한 유지 |
| 19. EchoRoad 인증·만료·제보 집계 | 추가 결함 미발견: `EchoRoadClientTest`, `EchoConnectionTest`, 서버 unittest; 운영 서버 부하 시험은 아님 |
| 20. 저장 복구·개인정보 삭제·알림 중복 | 추가 결함 미발견: `StorageTest`, `StorageRecoveryTest`, `PlatformTest` |

## 전체 검증

core 189개 + Android 299개 = 488개 통과, 실패·오류·skip 0개입니다. 서버 25개·도구 60개도 통과했습니다. 공식 SDK 포함 release(미서명)·휴대폰 검토본(기존 테스트 서명) 빌드 및 lint 통과. [검증 기록](verification.json). 새 APK 실행 여부는 빌드 성공과 분리합니다. 실제 S20+ 및 공식 Pleos 에뮬레이터에서 이번 수정본을 실행했다고 주장하지 않습니다. 제출용 `apk/`와 main은 변경하지 않습니다.

## 실제 외부 서비스 표본 확인

[실제 HTTP 결과](live-navigation.json): 지도 타일, 서울역 검색, 서울역→서울시청 자동차 경로 3건 모두 HTTP 200. 경로 응답은 약 4.009km, 도로 좌표 116개, 안내 단계 7개입니다. 실제 도로 주행이나 실시간 교통 검증은 아니며, 특정 시점의 제한된 서버 표본 검사입니다.

Lint: 오류 0개, 경고 14개(라이브러리 최신 버전 알림 10개, 알림 예약 토큰의 동기 저장 3개, XML 도구용 MapView 생성자 1개). 이번 변경과 무관한 의존성 일괄 업그레이드나 알림 저장 순서 변경은 하지 않았습니다.

실행 명령:

```sh
source scripts/env.sh
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assemblePhonePreview --console=plain
python3 -m unittest discover -s server -v
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-navigation-live.py --output docs/release-check/2026-10-28/live-navigation.json
```

[수정 소스 GitHub CI](https://github.com/hundol047/HYUNDAI/actions/runs/40654183365) 최종 통과. 현재 소스 debug 및 테스트 서명 release APK의 일반 Android 에뮬레이터 실행 검사도 통과했습니다. 공개 검토용 APK 자체의 S20+ 실기기 검사와 공식 Pleos 차량 이미지 검사는 별개이며 미수행입니다.
