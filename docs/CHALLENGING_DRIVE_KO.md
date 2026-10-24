# 복잡한 도로 모의 주행 · 웹 0.10.1-r2

2026-10-23 실제 Chromium에서 목적지 검색 → 실제 자동차 경로 계산 → 안내 시작 → 경로를 따라 GPS 이동 → 도착까지 실행했습니다. 지도는 OSM, 상호·주소는 Nominatim, 도로 경로는 OSRM의 실제 HTTPS 응답입니다. 서버 응답을 가로채거나 합성 경로로 바꾸지 않았습니다. **GPS와 시험 시계만 합성이며 실기기·실차 주행 시험이 아닙니다.** 공유 Kotlin NavigationTracker와 브라우저 지도를 실행했습니다. Android APK는 기존 서명 0.10.1 파일입니다.

| 실제 구간 / 목적지 | 경로 거리 | 시험 위치 | 도로 타일 확인 지점 | 결과 |
|---|---:|---:|---:|---|
| 서울 종로·세종대로·삼청로·북촌로5길 / Blue Bottle Coffee | 1,531.2m | 253 | 15 | 도착, 잘못된 이탈 판정 0 |
| 파리 개선문 회전교차로·샹젤리제 / Ladurée | 1,671.4m | 275 | 16 | 6번째 출구 안내 후 도착, 잘못된 이탈 판정 0 |
| 샌프란시스코 Lombard 연속 급커브·Leavenworth / Caffe Trieste | 1,539.5m | 254 | 15 | 도착, 잘못된 이탈 판정 0 |

총 **4,742.1m / GPS 입력 782개 / 도로 타일 확인 46회**입니다. 확인 시 화면과 겹치는 타일은 모두 이미지 디코딩이 끝났고 투명도가 해제됐습니다. 확인 사이 모든 프레임에서 지도가 완전히 준비됐다는 뜻은 아닙니다. GPS는 경로를 6m 간격으로 보간하고 약 1초의 시험 시각 간격으로 입력했습니다. 실제 대기와 렌더링은 유지하면서 약 6배속으로 진행했습니다. 회전부는 실제 폴리라인 방향을 사용했고 순간이동을 넣지 않았습니다. Lombard는 회전이 있는 구간 내부에서 출발하며, 첫 250m의 누적 방향 변화는 약 1,146도입니다. 경사·차선·운전 난이도 자체를 측정한 것은 아닙니다.

검사한 내용은 위치에 따른 지도 중심 추적, 경로 진행 오차 70m 이내, GPS 추적 유지, 잘못된 이탈 판정, 남은 거리·회전 문구 변화, 도착 판정, 주행 중 목적지 입력·지도 드래그 잠금입니다. 도착 판정은 두 번 연속 목적지 인근 저속 위치를 받은 뒤 이뤄졌고 30m 안쪽에서 안내를 끝냈습니다. 도로를 보기 위한 확대 수준은 주차 중 **17**로 선택했습니다.

## 가게 표시 확인과 남은 한계

세 목적지는 실서버 검색 결과의 가게명·주소·출처를 표시했고, 그 좌표의 목적지 핀과 실제 도로 접근 지점까지의 경로를 만들었습니다. 화면을 직접 확인해 주변 가게 이름과 POI 아이콘이 타일 위에 보이는 것도 확인했습니다. 파리의 Pharmacie de l'Étoile·Bowling Foch, 샌프란시스코의 Grant & Green 등의 표시는 지도 확대 수준과 가시 영역에 따릅니다. 이미지의 모든 상호를 OCR로 대조하거나 전체 가게 목록과 비교한 검사는 아닙니다.

**모든 가게가 검색·표시되는 것은 아닙니다.** 사전 실검색에서 `Cafe Onion Anguk Seoul`과 `어니언 안국`은 결과가 없었습니다. 이름·주소·현지어를 바꾸거나 앱의 외부 지도 추가 검색을 사용할 수 있습니다. 같은 날 서울 기준점의 별도 주변 추천 제공자 Overpass는 **18초 시간 초과**였으므로, 자동 주변 가게 추천의 실데이터 연결 성공으로 보고하지 않습니다. 기존 예시 장소를 실제 조회 성공으로 바꾸지 않았습니다. [검색 누락](../test-fixtures/validation/challenging-drive-r2/search-miss.json), [주변 제공자 결과](../test-fixtures/validation/challenging-drive-r2/nearby-provider.json).

## 녹화 검토에서 고친 부분

첫 시험 영상에서 좌·우회전 문자 화살표가 브라우저 글꼴에 따라 네모로 깨졌습니다. 웹 안내의 회전 아이콘을 글꼴과 무관한 로컬 SVG로 바꿨습니다. 화면의 회전 설명은 접근성 텍스트로 유지하고 중복 아이콘은 스크린리더에서 숨깁니다. GPS 갱신 때 같은 아이콘 DOM을 계속 만들지 않습니다.

[수정 전 화면](screenshots/drive-before-arrow.png)과 [수정 후 서울 화면](screenshots/drive-r2-seoul-middle.png). 수정 후 세 구간을 모두 다시 실행해 좌·우회전·회전교차로·도착 SVG를 확인했습니다. 기본 15개·내비게이션 13개·상세 조작 12개·도로 타일 7개·음성/언어 14개, 총 61개의 관련 회귀 검사도 통과했습니다. [집계](../test-fixtures/validation/challenging-drive-r2/regressions.json). 시험 하네스에는 SVG 도형 존재와 지도 중심 추적 검사를 넣었습니다.

## 직접 실행한 영상과 원본 근거

[모의 주행 MP4](video/CabinOS_ONE_challenging_drive_v0101r2.mp4)는 Playwright가 실행 중인 브라우저 화면을 연속 녹화한 영상입니다. 사진 슬라이드나 생성 영상이 아닙니다. 세 녹화 클립을 순서대로 연결하고 MP4로 인코딩했습니다. 가속 GPS라는 안내가 화면에 표시되며 약 **2분 46초**, 음성 녹음은 없습니다. 장면별 챕터를 포함합니다. Chromium에서 실제 재생, 30·85·130초 탐색, HTTP 부분 요청도 확인했습니다. [재생 검사](../test-fixtures/validation/challenging-drive-r2/video-playback.json). Android 실기기 영상으로 표기하지 않습니다.

- [서울 도심 교차로](screenshots/drive-r2-seoul-middle.png)
- [파리 회전교차로](screenshots/drive-r2-paris-middle.png)
- [Lombard 연속 급커브](screenshots/drive-r2-sf-hairpins.png)
- [가게 목적지 도착](screenshots/drive-r2-sf-finish.png)
- [전체 GPS·타일·지연·실제 요청 기록](../test-fixtures/validation/challenging-drive-r2/results.json), 같은 폴더의 원본 검색·경로 JSON
- [연속 녹화 클립 시간](../test-fixtures/validation/challenging-drive-r2/recording.json)

시험 중 GPS 처리+화면 갱신 p95는 서울 3.9ms, 파리 3.3ms, 샌프란시스코 3.7ms였습니다. 이 클라우드 Chromium의 해당 짧은 경로 측정값이며 실기기 FPS나 네트워크 응답 시간을 뜻하지 않습니다. 기존 대형 경로 스트레스 검사는 [별도 문서](MAP_PERFORMANCE_KO.md)를 참고하세요.

재현은 사이트 빌드와 내부 서버 실행 후 `node scripts/web/verify-challenging-drive-live.cjs`입니다. Chromium·Playwright·Playwright용 FFmpeg 경로가 필요합니다. `CABIN_DRIVE_OUT`으로 출력 폴더를 바꿀 수 있습니다. 이 검사는 외부 공개 제공자를 사용하므로 기본 CI에는 넣지 않았습니다. 검사를 반복하거나 넓은 영역의 지도 타일을 일괄 내려받지 마세요. 보이는 지도 범위의 정상 SDK 요청만 사용합니다.

공개 웹사이트는 GitHub Pages 활성화가 아직 필요합니다. [배포 상태와 APK/웹 파일](WEB_DEMO.md). 운영용 상호 데이터의 완전성과 가용성은 별도 계약 제공자 또는 자체 서버가 필요하며, 이 표본 시험으로 카카오맵 수준의 도로·교통·상호 정확도를 보장하지 않습니다.
