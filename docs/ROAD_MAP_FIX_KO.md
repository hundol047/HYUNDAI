# 도로 지도 표시 수정 · 0.10.1

내비게이션 화면의 격자/빈 지도를 확인하고, Android GPS와 지도 중심 연결 및 도로 타일 상태 표시를 수정했습니다. [실제 Android 지도 표시 화면](screenshots/navigation.png)을 확인하세요.

## 원인과 수정

- **경로 계산 전 GPS가 지도에 전달되지 않음:** 기존 Android UI는 경로 추적기의 위치만 지도에 전달했습니다. 실제 위치 수신기는 경로 시작 전에도 좌표를 받지만 지도는 기본 중심에 머물렀습니다. 최신 유효 GPS를 별도의 `mapFix`로 전달해 첫 수신 즉시 해당 위치의 거리 배율로 이동합니다. 경로가 없을 때 경로 선을 만들지 않습니다. 신호가 부정확/오래됨/배경 전환이면 최신 위치 자격을 제거합니다.
- **실제 도로 타일 표시와 GPS/경로 성공을 구분하지 않음:** Android와 웹 모두 현재 화면에 보이는 지도 타일이 실제 비트맵으로 준비됐는지 확인합니다. 이전 지역의 타일이나 GPS 성공으로 빈 지도를 정상 처리하지 않습니다. 로딩·일부 표시·실패·오프라인을 표시하고 주차 중 명시적인 재시도를 제공합니다. 실패해도 목적지/경로/확대 위치를 지우지 않습니다.
- **Android 갤러리의 격자 이미지:** 기존 `navigation.png`는 타일을 끈 오프라인 회귀 검사였습니다. 실제 OSM 타일을 내려받는 Android Compose/osmdroid 실행 화면으로 교체했습니다. 오프라인 회귀 검사 이미지는 [오프라인 검사 화면](screenshots/navigation-offline-fixture.png)으로 구분해 보관합니다.

수신 전에는 세계 지도를 보여주며 임의의 좌표를 사용자의 위치로 표시하지 않습니다. OSM 도로 지도와 경로 선은 별도입니다. 네트워크가 없고 이 지역의 캐시도 없으면 전 세계 도로를 만들어 표시할 수 없으므로 미표시 사유를 안내합니다. 지도 타일을 앱에 대량 포함하거나 오프라인 지도로 사전 내려받지 않습니다.

## 실제 Android 지도 실행 근거

`RoadMapLiveTest`를 실제 외부 네트워크로 별도 실행했습니다. 이 검사는 기본 CI의 합성 검사와 분리됩니다.

- 실제 `NavigationService`에서 OSRM 경로를 요청했습니다. 서울역 주변 → 서울시청 경로의 도로 형상은 **102개 좌표**입니다.
- 실제 Android Compose의 MapView가 OSM 타일을 요청·디코딩·렌더링했습니다. 현재 화면의 **16개 타일 모두 표시**된 뒤 화면을 캡처했습니다. 제공한 비트맵 fixture나 HTML 지도 이미지를 Android 화면에 붙인 결과가 아닙니다.
- [실행 결과 JSON](../test-fixtures/validation/roads-native-live.json) · [Android 화면](screenshots/navigation-roads.png).
- **Robolectric 네이티브 그래픽 모드의 Android UI 실행**입니다. 물리 기기/차량 캡처가 아니며 GPS는 공개 장소의 시험 좌표입니다. 실기기 GPS·실차 동작 검증을 대신하지 않습니다.

```bash
source scripts/env.sh
./gradlew -PliveRoadTiles :app:testDebugUnitTest --tests io.cabinos.one.RoadMapLiveTest
```

기본 Android 검사는 외부 서버 없이 실행하며 위 클래스를 제외합니다. 실서버 검사의 성공을 기본 CI가 매번 보장하는 것으로 해석하면 안 됩니다.

## 회귀 검사

Android에서는 경로 전 GPS 중심·표식, 오래된 좌표 제거, 화면별 타일 집계, 일부 타일 실패, 재시도, 오프라인 캐시 구분을 검사합니다. 웹에서는 현재 화면 타일 준비·로딩 실패·GPS와 지도 상태 분리·재시도·다른 지역 이동·캐시/오프라인·수명 종료·모바일 배치를 검사합니다. 합성 비트맵은 상태 처리용 시험 입력이며 실제 도로 증거는 위 라이브 검사와 별도입니다.

```bash
node scripts/web/verify-road-tiles.cjs
node scripts/web/verify-navigation-live.cjs
```

서명된 새 APK로 업데이트해야 Android 수정이 반영됩니다. 웹 ZIP도 같은 버전으로 다시 배포해야 합니다. GitHub Pages 활성화와 Release 첨부 업로드의 환경 제한은 [배포 안내](WEB_DEMO.md)에 별도로 기록합니다.

최종 기본 회귀: 코어 93개 + Android 59개 = 152개, 브라우저 115개 통과. Android 실서버 지도 검사는 위 별도 1개입니다. CPU 4배 감속·10만 지점 경로·120개 GPS 입력의 웹 측정은 초기 표시 606.9ms, 갱신 p95 14.8ms, 최대 29.7ms였습니다. 실기기 FPS나 네트워크 지연을 뜻하지 않습니다. [측정](../test-fixtures/validation/map-performance-v0101.json).

현재 웹도 실제 OSM·Nominatim·OSRM 연결과 확대/축소·드래그·안내 시작을 통과했습니다. [실제 브라우저 기록](../test-fixtures/validation/roads-web-live.json) · [화면](screenshots/roads-web-live-guidance.png). 브라우저 GPS는 공개 장소 시험 좌표입니다.

## 웹 화면 크기 전환 추가 수정 · r1

GitHub Chromium 검사에서 주변 지도를 모바일 크기로 바꾸면 SVG 핀이 사라지는 것을 발견했습니다. 즉시 크기를 바꾸는 재현에서 경로 속성이 `M0 0`이 되고 지도 중심이 의도와 다르게 이동했습니다. Leaflet의 창 크기 처리와 ResizeObserver가 중복 실행되고 이동 애니메이션이 겹친 경우였습니다.

내비·주변 지도 모두 ResizeObserver 한 곳에서 크기를 처리하고 먼저 이동 애니메이션을 정리하도록 수정했습니다. 이동 중 화면 크기를 바꾸는 실제 Chromium 검사에서 핀과 지도 중심이 유지됐고, 기존 확대/축소·터치·성능 검사도 통과했습니다. Android 소스와 APK는 같은 0.10.1입니다. 웹 배포 파일만 `0.10.1-r1`로 구분합니다.

웹 r1의 재현/핀 좌표 기록: [JSON](../test-fixtures/validation/road-resize-r1.json) · [모바일 화면](screenshots/map-resize-r1.png). 실제 지도 연결도 다시 통과했습니다: [웹 기록](../test-fixtures/validation/roads-web-live-r1.json). 같은 CPU 4배 부하에서 초기 표시 629.8ms, GPS 처리·화면 갱신 p95 10.3ms, 최대 13.8ms를 관찰했습니다. [r1 측정](../test-fixtures/validation/map-performance-v0101-r1.json).
