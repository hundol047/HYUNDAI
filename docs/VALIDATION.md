# 검증 결과

팀 **퓨처랩스** · CabinOS ONE `0.5.0`.

## 최종 통과

| 검사 | 결과 | 범위 |
|---|---|---|
| `:core:test` | **74개 통과, 실패/스킵 0** | 상태/충돌/주차 디바운스/오래된 신호/신호 복구/출처 변경/역순 신호, 공유 일정, 세션 중단·복원, 위험 점수·필터·쿨다운, 한국어 명령 안전 제한, 백그라운드 신뢰 초기화·삭제 제한·통합 시나리오 |
| `:app:testDebugUnitTest` | **22개 통과, 실패/스킵 0** | 실제 Android Activity의 Compose 전환·정차/신호 대기 제한·설정 화면 유지·집중 시작·주행 복귀, Room 트랜잭션/설정/이력/일시정지 체크포인트 복원, 모드 복원, 알림 토큰·권한·취소, 이력 보관·삭제 |
| `:app:assembleDebug` | 통과 | 개발용 APK 생성 |
| `:app:assembleRelease` | 통과 | R8 코드 최적화·리소스 축소, release 변형 |
| `:app:lintDebug` | 통과 | 오류 0, 경고 4건 (`DiscouragedApi` 1, 기존 동기식 SharedPreferences 기록 관련 `ApplySharedPref` 3) |
| `apksigner verify --verbose --print-certs` | 통과 | 전용 3072-bit RSA 팀 릴리스 인증서, APK v3 서명 |
| `zipalign -c -P 16 4` | 통과 | APK ZIP 정렬. 별도 실차/모든 native ELF 페이지 크기 호환성 검증을 뜻하지 않음 |
| APK manifest 확인 | 통과 | `io.cabinos.one`, minSdk 29, targetSdk 35, **debuggable=false** |

총 **96개 JVM/Android 테스트 통과**. **브라우저 기본 15개·주변 지도 11개·내비게이션 13개·색상 8개, 총 47개가 통과했습니다.** 최종 결과는 아래 JSON 증거 파일과 대조합니다. 상세 집계는 [test-results.json](validation/test-results.json), APK 크기/체크섬/인증서는 [apk.json](validation/apk.json), 파일 체크섬은 [apk/SHA256SUMS](../apk/SHA256SUMS)에 있습니다.

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
node scripts/web/record-demo.cjs
```

## UI 렌더링 근거

Compose 실제 화면을 Robolectric 네이티브 그래픽 모드(API 29, 1280×800)에서 렌더링했습니다. 동작 검증 후 Activity의 실제 View를 Canvas로 캡처했습니다. 별도의 HTML, 가짜 화면, AI 합성 이미지가 아닙니다. **실차 또는 에뮬레이터 스크린샷으로 표기하지 않습니다.**

- [정차 대시보드](screenshots/parked.png): StopSense와 CabinOS 동시 표시.
- [집중 화면](screenshots/focus.png): 공유 출발/활동 가능 시간, 실제 세션 카운트다운, 할 일, 일시정지/종료/오디오 제어.
- [주행 화면](screenshots/driving.png): EchoRoad 우선, 정차용 조작 제한.
- [내비게이션](screenshots/navigation.png): 합성 파리 경로/GPS를 사용한 실제 Compose MapView 렌더링. 지도 타일은 오프라인이며 기기 주행 캡처가 아님.

집중 중에는 StopSense를 요약 바에 유지해 타이머·조작부가 화면 아래로 가려지지 않도록 했습니다. 타이머 갱신 시 내비게이션 그래프가 재생성되지 않는지도 앱 테스트에서 확인했습니다.

## 기기/실차 검증의 한계

현재 머신에는 `/dev/kvm`이 없어 Android API 35 에뮬레이터를 소프트웨어 CPU 모드로 구동했습니다. Debug APK 설치와 Activity/프로세스 실행은 확인했지만 Android System UI 응답 지연과 빈 UIAutomation root로 장치 UI 스모크 검증을 완료하지 못했습니다. 안정 채널 에뮬레이터에서도 Android system_server의 SIGABRT가 관찰되어 이 머신의 장치 실행 검증은 중단했습니다. `scripts/device_smoke.py` 실행은 이 환경에서 **통과하지 않았으며**, 위 66개 테스트에 포함하지 않습니다. Release APK의 실제 장치 전체 실행 시나리오도 아직 통과했다고 주장하지 않습니다.

에뮬레이터 설치 성공, Robolectric UI 동작, 실제 차량 동작은 각각 다른 검증입니다. 심사 환경/하드웨어 가속 에뮬레이터에서 서명된 제출 APK를 설치하고 장치 스모크 검사를 추가 수행해야 합니다.

한국어 TTS/STT는 실제 Android API 어댑터를 제공하지만 해당 언어팩/인식 서비스가 있는 실제 기기에서의 음성 품질·서비스 동작은 미검증입니다. 앱 테스트의 한국어 명령 라우팅은 로컬 엔진을 검증합니다.

초기에는 Pleos 공식 문서 접근이 프록시 403으로 차단됐지만, 환경 재시작 후 공식 개발·SDK 문서를 확인했습니다. 문서 확인과 최소 환경 대조를 완료했으며, Pleos 실차 SDK 어댑터·충전/위험/경로 신호와 Pleos 실행 호환성은 **미검증·미연동**입니다. OSM 기반 경로 서비스의 0.5.0 라이브 확인은 아래에 별도 기록합니다. 관련 제약은 [SDK_CAPABILITIES.md](SDK_CAPABILITIES.md)와 [LIMITATIONS.md](LIMITATIONS.md)에 명시했습니다. [GitHub Actions Android 검증](https://github.com/hundol047/HYUNDAI/actions/runs/39636898536)에서도 JVM/Android 테스트·JVM/JS 빌드·Debug/Release 빌드·Lint가 통과했습니다. 해당 실행의 소스는 `c5e9acad55301c4e924f7fa62a75a9da6c758f43`입니다. 웹 배포 워크플로는 별도 결과이며 공개 URL 성공과 구분합니다.

## 실행 영상과 재점검

[최종 MP4](video/CabinOS_ONE_demo.mp4)는 실제 Chromium 조작을 CDP로 녹화하고 한국어 자막을 넣었습니다. 그림을 이어 붙인 모형 영상이 아닙니다. 초안 영상에서 할 일 버튼 제출 실패를 확인했고 관련 폼 처리, 주행 상태 문구, 한 번 조작하는 위험 안내와 연결 미확인 화면을 수정한 뒤 다시 촬영했습니다. [영상 검토 내역](VIDEO_REVIEW.md)에 근거와 수정 사항을 기록했습니다. 영상의 Android 화면은 실제 Compose 렌더링을 보여주는 갤러리이며 APK의 실기기 실행 영상은 아닙니다.

지도 추가 이전 소스 `a970ea57679e18692ca71c6b8a613b59301c8638`의 [Android 원격 검증](https://github.com/hundol047/HYUNDAI/actions/runs/39638061704)도 성공했습니다. [웹 원격 실행](https://github.com/hundol047/HYUNDAI/actions/runs/39638061596)은 사이트 빌드·브라우저 검사까지 성공했으며, Pages 미활성화에 따른 설정 오류로 배포가 중단됐습니다. 환경 재시작 후 Pages 생성도 시도했지만 GitHub 연동 앱의 API 권한 제한으로 HTTP 403이 반환됐습니다.

## 0.3.0 지도 추가 후 검증

이번 검사에서 모바일 크기 변경 후 지도 마커가 화면 밖으로 밀리는 문제를 발견해 ResizeObserver와 영역 재조정을 추가했습니다. 지도에서 외출 계획을 선택할 때 공유 일정이 왕복 이동 전체를 확보하도록 정정했습니다. Android Lint의 K2/UAST 내부 오류는 별도 실행에서 통과했으며, 재현 위험을 줄이도록 테스트·Release 컴파일·Lint 호출을 분리했습니다. 검사나 단언을 끄지 않았습니다.

위에 링크된 기존 GitHub Actions 성공은 이전 버전의 근거입니다. 새 지도 버전의 원격 실행 결과는 해당 커밋의 Actions를 별도로 확인해야 합니다. 0.3.0 당시 OSM/Overpass 접속은 CONNECT 403이었습니다. 0.5.0에서 OSM 타일 연결은 성공했고 Overpass는 별도 502/시간 초과로 구분합니다.

## 0.4.0 이전 검증 기록

0.4.0 커밋은 핵심 67개·Android 19개와 합성 프로토콜 브라우저 검사를 통과했습니다. 당시 제공자 접속은 CONNECT 403이어서 녹화의 내비게이션 장면에 합성 응답/GPS를 사용했습니다. 현재 0.5.0의 실서버·테마·영상 결과는 아래와 최상단 집계가 기준입니다. 이전 Actions 링크는 해당 과거 커밋의 근거입니다.

## 0.5.0 실제 서버·테마·정밀도 보완

- **실제 요청 9건 통과:** OSM 타일 PNG + 서울/파리/뉴욕/도쿄의 Nominatim 검색과 OSRM 자동차 경로. HTTP 상태·응답 해시·도로 형상/안내 수를 [실서버 기록](validation/live-navigation-results.json)에 보관합니다. 기록된 원본 도로 응답은 출처와 함께 Android 회귀 fixture에도 넣어 실제 형식의 파싱과 안내 순서를 확인합니다. CI fixture의 성공은 새로운 실시간 요청 성공과 구분합니다.
- **실제 Chromium 연결 통과:** TLS 검증·기존 세션 프록시를 유지하고 OS의 공개 CA를 Chromium NSS에도 등록했습니다. 실제 서울 지도 타일·서울시청 검색·도로 경로를 화면에 표시하고 공통 엔진에서 안내를 시작했습니다. [브라우저 근거](validation/live-browser-results.json), [지도 화면](screenshots/seoul-live-guidance.png). GPS는 미리 정한 공개 장소의 합성 위치이며 물리 GPS·실도로 운행은 미검증입니다.
- **별도 주변 장소 서비스:** Overpass GET은 502, 진단한 상태/POST 요청은 시간 초과였습니다. [결과](validation/live-nearby-results.json). Nominatim/OSRM의 성공을 Overpass 장소 추천 성공으로 확대하지 않습니다. 서비스 오류 시 예시/기존 결과와 연결 실패를 표시합니다.
- **시각 설정:** 5개 실내 테마·강조색 보정·앱 창 밝기·야간 타일 필터·복원/초기화·주행 변경 차단을 검사했습니다. 모든 프리셋/밝기와 검정/흰색/빨강/어두운 입력의 글자/버튼 대비는 최소 4.5:1입니다. [테마 결과](validation/appearance-results.json).
- **내비게이션 보완:** 순간 GPS 도약 거부, 방향/진행 거리의 일관성, 교차로 15m 통과 후 다음 안내, 목적지와 차량 접근 지점 차이/확인, 도착 완료/위치 불확실 상태 ETA 표시, 큰 회전 기호를 추가했습니다. 정밀 지도 매칭·교통/차선 데이터·카카오맵과 동일한 정확도를 인증한 것은 아닙니다.
- **실행 검사 안정화:** Room 응답을 기다리는 Robolectric 검사에서 MainLooper의 대기 콜백을 명시적으로 진행시켰습니다. 20초 대기와 기존 안전·저장/UI 단언을 유지하며, 실제 앱의 동작이나 검사를 생략하지 않았습니다.

직접 재현: `python3 scripts/check-navigation-live.py --all-regions`, `node scripts/web/verify-navigation-live.cjs`. 클라우드 Chromium에서 프록시 CA 신뢰가 필요하면 `scripts/prepare-browser-trust.sh`를 사용합니다. 작업 폴더 밖의 NSS 저장소 쓰기 권한이 필요할 수 있습니다. 기존 인증서/키를 교체하거나 TLS 검증을 해제하지 않습니다. 환경 상태 API는 network.state=unknown이어서 환경 전체 게시 완료는 주장하지 않습니다.
