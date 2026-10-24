# 검증 결과

팀 **퓨처랩스** · CabinOS ONE `0.10.5`.

[0.10.0 음성 기능·지원 언어·기기 한계](VOICE_NAVIGATION_KO.md)와 [실제 제공자 요청](validation/voice-review-live-providers.json)을 함께 확인하세요.

[0.10.1 도로 지도 표시 수정과 별도 Android 실서버 검사](ROAD_MAP_FIX_KO.md). 실제 타일 16개가 그려진 내비 화면을 갤러리에 제공합니다.

[웹 r2 복잡한 경로 모의 주행](CHALLENGING_DRIVE_KO.md): 실제 서버·합성 GPS로 3개 도시 4.742km, 782개 위치, 46개 도로 타일 확인 지점을 통과했습니다. 상호 검색 누락과 주변 추천 서버 시간 초과는 별도로 기록했습니다.

[0.10.2 서울시청→미래캠퍼스 실제 경로 모의 주행](SEOUL_YONSEI_DRIVE_KO.md): 113.2km, 위치 입력 6,930개, 도로 확인 53곳. 검색 공백 보정·고속도로 방향 표지·도착 표시를 보완했습니다.

[0.10.3 글로벌 모의 주행](WORLD_DRIVE_KO.md): 21개국·26개 지역, 121.5km·24,355개 위치·202곳 지도 검사. 도착 전 완료형 문구를 수정했습니다.

[0.10.4 장시간 모의주행](WORLD_SOAK_KO.md)와 [실행 영상 재검토](VIDEO_REVIEW_V0104.md)를 추가했습니다. 동일 최종 엔진으로 246개 GPS 오차·방향 누락·정지·신호 끊김 조건도 검사했습니다. 날짜변경선 표시는 [8개 브라우저 검사](validation/date-line-results.json)와 Android 실제 지도 선·핀 좌표 검사로 검증했습니다. 장시간 검사는 별도 보고서의 완료 상태를 확인하세요.

0.10.5: [검색 누락·주변 추천 후속 수정](SEARCH_RECOVERY_KO.md). 동명 업종·지역 혼동, 지역 후보·직접 지도 목적지·취소·늦은 응답을 보완했습니다. 최신 [빌드 해시](validation/build-v0105.json)와 [기록 경로 50개 재생](validation/replay-soak-v0105.json)은 이번 빌드의 검사입니다. 과거 246개 GPS 오차 조건과 5시간 실서버 검사는 위 0.10.4 기록으로 구분합니다.

[공개 사이트 배포 확인](validation/pages-v0105.json): 2026-10-24 16:18 KST, 기존 검증된 앱의 Pages 배포 두 번째 실행 성공. HTTPS 접속·데모 조작·Android 갤러리·APK 일치·실제 지도 타일·모바일 배치·영상 재생/탐색을 확인했습니다.

## 최종 통과

| 검사 | 결과 | 범위 |
|---|---|---|
| `:core:test` | **120개 통과, 실패/스킵 0** | 상태/충돌/주차 디바운스/오래된 신호/신호 복구/출처 변경/역순 신호, 공유 일정, 세션 중단·복원, 위험 점수·필터·쿨다운, 한국어 명령 안전 제한, 백그라운드 신뢰 초기화·삭제 제한·통합 시나리오 |
| `:app:testDebugUnitTest` | **72개 통과, 실패/스킵 0** | 실제 Android Activity의 Compose 전환·정차/신호 대기 제한·설정 화면 유지·집중 시작·주행 복귀, Room 트랜잭션/설정/이력/일시정지 체크포인트 복원, 모드 복원, 알림 토큰·권한·취소, 이력 보관·삭제 |
| `:app:assembleDebug` | 통과 | 개발용 APK 생성 |
| `:app:assembleRelease` | 통과 | R8 코드 최적화·리소스 축소, release 변형 |
| `:app:lintDebug` | 통과 | 오류 0, 경고 5건 (`DiscouragedApi` 1, 기존 동기식 SharedPreferences 기록 관련 `ApplySharedPref` 3, 코드에서 생성하는 RoadMapView의 XML 도구용 생성자 관련 `ViewConstructor` 1) |
| `apksigner verify --verbose --print-certs` | 통과 | 전용 3072-bit RSA 팀 릴리스 인증서, APK v3 서명 |
| `zipalign -c -P 16 4` | 통과 | APK ZIP 정렬. 별도 실차/모든 native ELF 페이지 크기 호환성 검증을 뜻하지 않음 |
| APK manifest 확인 | 통과 | `io.cabinos.one`, minSdk 29, targetSdk 35, **debuggable=false** |

총 **192개 JVM/Android 테스트 통과**. **브라우저 기본 15개·주변 지도 16개·내비게이션 14개·색상 8개·가게 검색 24개·조작/수명/지연 12개·GPS 15개·음성/언어 15개·도로 타일 8개·지도 조작/성능 8개·날짜변경선 8개, 총 143개가 통과했습니다.** Python 선택적 검색 서버는 별도 [11개가 통과](validation/place-gateway-tests.json)했습니다. 최종 결과는 아래 JSON 증거 파일과 대조합니다. 상세 집계는 [test-results.json](validation/test-results.json), APK 크기/체크섬/인증서는 [apk.json](validation/apk.json), 파일 체크섬은 [apk/SHA256SUMS](../apk/SHA256SUMS)에 있습니다.

브라우저 검사는 실제 Chromium에서 공유 Kotlin/JS 엔진을 로드해 실행했습니다. 입력 제출/Enter·문자 이스케이프, 주행 명령 차단, D 기어 정지, 주차 디바운스, 일정 단축, 일시정지와 출발 타이머 분리, 어시스턴트 열린 중 주행 전환, 설정 적용, 미연결 실차 UNKNOWN, 실제 브라우저 오프라인, APK 다운로드 바이트 일치, 390px 모바일 배치, 실제 MP4 재생과 탐색을 확인했습니다. 결과: [browser-results.json](validation/browser-results.json).

지도 검사에는 공유 거리/왕복 계획, 실제 MapView 생성과 선택, 프로토콜 파싱을 포함했습니다. 웹 지도 검사는 합성 Overpass 응답·위치 거부·실패·빈 결과·주행 전환·오프라인·모바일 마커 배치를 확인합니다. 지도 서버의 실제 연결 성공을 의미하지 않습니다. 글로벌 내비게이션은 추가로 한국/유럽/미주/일본/호주/남아공 좌표 계산, 날짜 변경선, GPS 정확도/노후/중복/도착, 재탐색 간격, 실제 Android LocationManager 콜백과 안전 잠금, OSRM/Nominatim JSON 파싱을 검사합니다. UI/프로토콜 검사는 합성 응답·GPS를 사용하며 실서버·실도로 성공을 뜻하지 않습니다. [내비게이션 결과](validation/navigation-results.json), [범위](NAVIGATION_KO.md)를 확인하세요. [지도 검사 결과](validation/map-results.json), [실서버 제한](MAPS_KO.md)을 함께 확인하세요.

최종 실행 명령:

```bash
source scripts/env.sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
./scripts/package-submission.sh
./scripts/build-web.sh
node scripts/web/verify.cjs
node scripts/web/verify-map.cjs
node scripts/web/verify-navigation.cjs
node scripts/web/verify-appearance.cjs
node scripts/web/verify-place-search.cjs
node scripts/web/verify-navigation-detail.cjs
node scripts/web/verify-road-tiles.cjs
node scripts/web/verify-gps.cjs
node scripts/web/verify-voice-navigation.cjs
node scripts/web/verify-map-performance.cjs
node scripts/web/verify-date-line.cjs
python3 -m unittest discover -s server -v
node scripts/web/record-demo.cjs
```

## UI 렌더링 근거

Compose 실제 화면을 Robolectric 네이티브 그래픽 모드(API 29, 1280×800)에서 렌더링했습니다. 동작 검증 후 Activity의 실제 View를 Canvas로 캡처했습니다. 별도의 HTML, 가짜 화면, AI 합성 이미지가 아닙니다. **실차 또는 에뮬레이터 스크린샷으로 표기하지 않습니다.**

- [정차 대시보드](screenshots/parked.png): StopSense와 CabinOS 동시 표시.
- [집중 화면](screenshots/focus.png): 공유 출발/활동 가능 시간, 실제 세션 카운트다운, 할 일, 일시정지/종료/오디오 제어.
- [주행 화면](screenshots/driving.png): EchoRoad 우선, 정차용 조작 제한.
- [내비게이션](screenshots/navigation.png): 실제 OSM 도로 타일·OSRM 경로를 표시한 Android Compose/osmdroid 렌더링. 공개 장소 GPS는 시험 입력이며 물리 기기/실차 캡처가 아님. 기본 CI의 오프라인 검사 화면과 별도로 촬영했습니다.

집중 중에는 StopSense를 요약 바에 유지해 타이머·조작부가 화면 아래로 가려지지 않도록 했습니다. 타이머 갱신 시 내비게이션 그래프가 재생성되지 않는지도 앱 테스트에서 확인했습니다.

## 기기/실차 검증의 한계

현재 머신에는 `/dev/kvm`이 없어 Android API 35 에뮬레이터를 소프트웨어 CPU 모드로 구동했습니다. Debug APK 설치와 Activity/프로세스 실행은 확인했지만 Android System UI 응답 지연과 빈 UIAutomation root로 장치 UI 스모크 검증을 완료하지 못했습니다. 안정 채널 에뮬레이터에서도 Android system_server의 SIGABRT가 관찰되어 이 머신의 장치 실행 검증은 중단했습니다. `scripts/device_smoke.py` 실행은 이 환경에서 **통과하지 않았으며**, 위 JVM/Android 테스트에 포함하지 않습니다. Release APK의 실제 장치 전체 실행 시나리오도 아직 통과했다고 주장하지 않습니다.

에뮬레이터 설치 성공, Robolectric UI 동작, 실제 차량 동작은 각각 다른 검증입니다. 심사 환경/하드웨어 가속 에뮬레이터에서 서명된 제출 APK를 설치하고 장치 스모크 검사를 추가 수행해야 합니다.

한국어 TTS/STT는 실제 Android API 어댑터를 제공하지만 해당 언어팩/인식 서비스가 있는 실제 기기에서의 음성 품질·서비스 동작은 미검증입니다. 앱 테스트의 한국어 명령 라우팅은 로컬 엔진을 검증합니다.

초기에는 Pleos 공식 문서 접근이 프록시 403으로 차단됐지만, 환경 재시작 후 공식 개발·SDK 문서를 확인했습니다. 문서 확인과 최소 환경 대조를 완료했으며, Pleos 실차 SDK 어댑터·충전/위험/경로 신호와 Pleos 실행 호환성은 **미검증·미연동**입니다. OSM 기반 경로 서비스의 0.5.0 라이브 확인은 아래에 별도 기록합니다. 관련 제약은 [SDK_CAPABILITIES.md](SDK_CAPABILITIES.md)와 [LIMITATIONS.md](LIMITATIONS.md)에 명시했습니다. [GitHub Actions Android 검증](https://github.com/hundol047/HYUNDAI/actions/runs/39636898536)에서도 JVM/Android 테스트·JVM/JS 빌드·Debug/Release 빌드·Lint가 통과했습니다. 해당 실행의 소스는 `c5e9acad55301c4e924f7fa62a75a9da6c758f43`입니다. 웹 배포 워크플로는 별도 결과이며 공개 URL 성공과 구분합니다.

## 실행 영상과 재점검

최신 0.10.5의 실행 영상·재검토 근거는 [검색 복구 보고서](SEARCH_RECOVERY_KO.md)에 있습니다. 이전 0.10.4의 두 영상은 [당시 보고서](VIDEO_REVIEW_V0104.md)에 보존했습니다. 아래 내용은 초기 영상에서 발견한 문제의 수정 기록입니다.

[0.5.0 MP4](video/CabinOS_ONE_demo.mp4)는 실제 Chromium 조작을 CDP로 녹화하고 한국어 자막을 넣었습니다. 그림을 이어 붙인 모형 영상이 아닙니다. 초안 영상에서 할 일 버튼 제출 실패를 확인했고 관련 폼 처리, 주행 상태 문구, 한 번 조작하는 위험 안내와 연결 미확인 화면을 수정한 뒤 다시 촬영했습니다. [영상 검토 내역](VIDEO_REVIEW.md)에 근거와 수정 사항을 기록했습니다. 영상의 Android 화면은 실제 Compose 렌더링을 보여주는 갤러리이며 APK의 실기기 실행 영상은 아닙니다.

지도 추가 이전 소스 `a970ea57679e18692ca71c6b8a613b59301c8638`의 [Android 원격 검증](https://github.com/hundol047/HYUNDAI/actions/runs/39638061704)도 성공했습니다. [웹 원격 실행](https://github.com/hundol047/HYUNDAI/actions/runs/39638061596)은 사이트 빌드·브라우저 검사까지 성공했으며, Pages 미활성화에 따른 설정 오류로 배포가 중단됐습니다. 환경 재시작 후 Pages 생성도 시도했지만 GitHub 연동 앱의 API 권한 제한으로 HTTP 403이 반환됐습니다.

## 0.3.0 지도 추가 후 검증

이번 검사에서 모바일 크기 변경 후 지도 마커가 화면 밖으로 밀리는 문제를 발견해 ResizeObserver와 영역 재조정을 추가했습니다. 지도에서 외출 계획을 선택할 때 공유 일정이 왕복 이동 전체를 확보하도록 정정했습니다. Android Lint의 K2/UAST 내부 오류는 별도 실행에서 통과했으며, 재현 위험을 줄이도록 테스트·Release 컴파일·Lint 호출을 분리했습니다. 검사나 단언을 끄지 않았습니다.

위에 링크된 기존 GitHub Actions 성공은 이전 버전의 근거입니다. 새 지도 버전의 원격 실행 결과는 해당 커밋의 Actions를 별도로 확인해야 합니다. 0.3.0 당시 OSM/Overpass 접속은 CONNECT 403이었습니다. 0.5.0에서 OSM 타일 연결은 성공했고 Overpass는 별도 502/시간 초과로 구분합니다.

## 0.4.0 이전 검증 기록

0.4.0 커밋은 핵심 68개·Android 19개와 합성 프로토콜 브라우저 검사를 통과했습니다. 당시 제공자 접속은 CONNECT 403이어서 녹화의 내비게이션 장면에 합성 응답/GPS를 사용했습니다. 0.5.0 당시 실서버·테마·영상 결과는 아래의 과거 기록이며, 현재 집계는 문서 최상단을 기준으로 합니다. 이전 Actions 링크는 해당 과거 커밋의 근거입니다.

## 0.5.0 실제 서버·테마·정밀도 보완

- **실제 요청 9건 통과:** OSM 타일 PNG + 서울/파리/뉴욕/도쿄의 Nominatim 검색과 OSRM 자동차 경로. HTTP 상태·응답 해시·도로 형상/안내 수를 [실서버 기록](validation/live-navigation-results.json)에 보관합니다. 기록된 원본 도로 응답은 출처와 함께 Android 회귀 fixture에도 넣어 실제 형식의 파싱과 안내 순서를 확인합니다. CI fixture의 성공은 새로운 실시간 요청 성공과 구분합니다.
- **실제 Chromium 연결 통과:** TLS 검증·기존 세션 프록시를 유지하고 OS의 공개 CA를 Chromium NSS에도 등록했습니다. 실제 서울 지도 타일·서울시청 검색·도로 경로를 화면에 표시하고 공통 엔진에서 안내를 시작했습니다. [브라우저 근거](validation/live-browser-results.json), [지도 화면](screenshots/seoul-live-guidance.png). GPS는 미리 정한 공개 장소의 합성 위치이며 물리 GPS·실도로 운행은 미검증입니다.
- **별도 주변 장소 서비스:** Overpass GET은 502, 진단한 상태/POST 요청은 시간 초과였습니다. [결과](validation/live-nearby-results.json). Nominatim/OSRM의 성공을 Overpass 장소 추천 성공으로 확대하지 않습니다. 서비스 오류 시 예시/기존 결과와 연결 실패를 표시합니다.
- **시각 설정:** 5개 실내 테마·강조색 보정·앱 창 밝기·야간 타일 필터·복원/초기화·주행 변경 차단을 검사했습니다. 모든 프리셋/밝기와 검정/흰색/빨강/어두운 입력의 글자/버튼 대비는 최소 4.5:1입니다. [테마 결과](validation/appearance-results.json).
- **내비게이션 보완:** 순간 GPS 도약 거부, 방향/진행 거리의 일관성, 교차로 15m 통과 후 다음 안내, 목적지와 차량 접근 지점 차이/확인, 도착 완료/위치 불확실 상태 ETA 표시, 큰 회전 기호를 추가했습니다. 정밀 지도 매칭·교통/차선 데이터·카카오맵과 동일한 정확도를 인증한 것은 아닙니다.
- **실행 검사 안정화:** Room 응답을 기다리는 Robolectric 검사에서 MainLooper의 대기 콜백을 명시적으로 진행시켰습니다. 20초 대기와 기존 안전·저장/UI 단언을 유지하며, 실제 앱의 동작이나 검사를 생략하지 않았습니다.

직접 재현: `python3 scripts/check-navigation-live.py --all-regions`, `node scripts/web/verify-navigation-live.cjs`. 클라우드 Chromium에서 프록시 CA 신뢰가 필요하면 `scripts/prepare-browser-trust.sh`를 사용합니다. 작업 폴더 밖의 NSS 저장소 쓰기 권한이 필요할 수 있습니다. 기존 인증서/키를 교체하거나 TLS 검증을 해제하지 않습니다. 환경 상태 API는 network.state=unknown이어서 환경 전체 게시 완료는 주장하지 않습니다.

v0.5.0 당시 Android 소스 `a0a0e48ee4ab11cdd993551dcbccce6416abe07d`의 [원격 테스트·Debug/Release 빌드·Lint](https://github.com/hundol047/HYUNDAI/actions/runs/39739092859)가 통과했습니다. 이후 웹 상단 시계를 ETA와 동일한 기기 시간대로 보정하고, America/New_York 시간대에서 내비게이션 브라우저 검사 13개를 다시 통과했습니다. APK 내용은 이 웹 시간대 수정으로 바뀌지 않습니다.

## 0.6.0 지도 조작·성능 재검토

[지도 성능 점검](MAP_PERFORMANCE_KO.md)에 6개 대륙 7도시의 실제 검색/경로 14건과 타일 1건, 실제 Chromium 확대/축소/드래그, 합성 100,000개 지점 경로의 CPU 4배 부하 비교를 기록했습니다. GPS마다 경로 Polyline을 다시 만들던 Android, 웹의 반복 중심 이동·DOM 재생성·ResizeObserver 누수와 확대 배율 덮어쓰기를 수정했습니다. 두 손가락 줌은 브라우저 터치 에뮬레이션으로 검증했으며 Android 실기기 FPS 검증은 아닙니다. 0.5.0 Actions 링크는 이전 소스의 근거입니다.

## 0.7.0 상호·경로 실제 표본과 오류 보완

[가게 검색 설명](PLACE_SEARCH_KO.md)에 실제 11개 검색 사례(9개 결과 있음·알려진 영어 상호 1개 누락·존재하지 않는 상호 대조 1개 빈 결과)와 12개 경로 요청(유효 미리보기 10개·과도한 출발점 이동 1개·HTTP400 요청 거부 1개)을 구분했습니다. [요약 JSON](validation/place-route-coverage.json), [실제 브라우저 결과](validation/place-search-live-browser.json), [자체 서버 실제 Nominatim 중계](validation/place-gateway-live.json). GPS는 합성 입력이고 실기기/도로 주행은 아닙니다. 카카오 API 실서버는 키 미등록으로 미검증입니다.

상호 ID·주소·제공되는 전화/영업시간/상세 링크, 같은 건물 지점 구분, 명시적 주변 반경·더 보기, GPS 없이 핀 표시, 오프라인/빈 결과/HTTP 오류 처리를 추가했습니다. 실제 뉴욕→런던 HTTP200 응답의 5,388km 출발점 오연결을 거부하고, 도로에서 떨어진 출발 GPS는 안내 시작을 잠급니다. 카카오 서버의 15개 공급자 페이지와 6개 앱 페이지 경계 누락, GPS 없이 가게 선택 후 재시도 간격 소모, 새 좌표 검색 때 남는 이전 미리보기도 수정하고 회귀 검사했습니다. CI의 실제 응답 fixture는 기록 재생이며 실시간 API 호출과 구분합니다. [가게 UI 검사](validation/place-search-results.json).

선택적 Python 서버는 인증·CORS·캐시 만료·오류 비노출·고정 공급자 호스트·카카오 페이지 경계를 검사했습니다. 서버의 실제 공개 HTTPS 배포, 상용 계정 등록과 키 발급은 포함하지 않습니다. 과거 0.5.0 영상은 현재 0.7.0 가게 기능을 촬영한 것이 아닙니다.

0.7.0 최종 지도 부하 재검사도 8개 통과했습니다. CPU 4배 감속·10만 지점 경로에서 초기 파싱/표시 약 1.30초, 120개 GPS 갱신의 p95 약 11.6ms, 최대 25.5ms입니다. [당시 결과](validation/map-performance-v070.json). 0.6.0의 전후 비교 기록을 덮어쓰지 않았습니다. 이 수치는 실제 Android 기기 FPS가 아닙니다.

## 0.8.0 조작·수명·오류·속도 재검토

[문제별 수정 보고서](DETAIL_REVIEW_KO.md)에 요청 취소/늦은 응답, GPS 품질·시각 순서, 화면 복귀, 오류 문구 유지와 시간 초과, 재계산/선택 지우기를 정리했습니다. 공유 경로의 점별 인덱스와 브라우저 JSON 왕복 복사를 줄였습니다. 같은 CPU 4배 부하에서 10만 지점 초기 표시가 수정 전 1.01–1.44초(2회)에서 약 0.57초(1회)로 줄었고, 안내 중 GPS 처리·갱신 p95는 8.3ms였습니다. 작은 합성 표본이며 Android 실기기 FPS나 외부 서버 지연 결과가 아닙니다. 수정 전 GPS 갱신 표본은 차량 P 재확인 미완료로 무효라 비교에서 제외했습니다.

새 세부 브라우저 12개와 Android 5개, 공유 회전점 2개를 기존 검사에 추가했습니다. Android 검사는 일관된 주입 시계를 사용하고, 공급자의 최소 시간/이동 필터가 중복/역순 입력을 숨기지 않도록 등록된 프레임워크 콜백에 검사 위치를 전달합니다. 기존 LocationManager 실제 시뮬레이션 검사는 유지합니다. [세부 결과](validation/navigation-detail-results.json), [성능 결과](validation/map-performance-v080.json).

## 0.9.0 GPS와 전체 기능 재점검

[기능별 객관적 판정](GPS_REVIEW_KO.md)에 정차 위치 수신·구독 재등록·권한 해제/재허용·오래된 위치·중단/복귀·네트워크 위치 우선순위·연속 정지·음성 재요청을 정리했습니다. 정차 좌표는 실제 LocationManager 테스트 제공자에 12초 이상 반복 전송해 갱신을 확인했습니다. GPS 입력/권한은 합성이며 물리 GPS 수신은 아닙니다. Android TTS API와 브라우저 디스패처 기록으로 복구 재요청을 확인했지만 실제 한국어 음질/기기 STT는 미검증입니다.

OSM 타일·주소 검색·OSRM 실제 요청 3개와 실제 Chromium 지도/경로/안내도 통과했습니다. GPS만 공개 장소의 합성 좌표입니다. Overpass는 재확인 요청도 HTTP 502여서 실제 추천 성공으로 판정하지 않았습니다. 실제 실패와 코드/fixture 통과를 구분합니다. [GPS 검사](validation/gps-results.json), [실제 제공자](validation/gps-review-live-providers.json), [실제 브라우저](validation/gps-review-live-browser.json), [주변 추천 실패](validation/gps-review-live-nearby.json).
