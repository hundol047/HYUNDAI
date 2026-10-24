# 26개 지역 글로벌 모의 주행·도착 안내 수정 (0.10.3)

2026-10-23, **21개국·6개 대륙·26개 지역**을 실제 OSM 지도·Nominatim 검색·OSRM 자동차 경로로 검사했습니다. 주행 위치는 검사용 합성 GPS이며 실제 차량·물리 GPS 시험이 아닙니다. **전 세계 모든 도로·모든 가게를 검사한 결과가 아닙니다.**

총 **121.521km**, 합성 위치 **24,355개**, 지도 확인 지점 **202곳**입니다. 관측한 위치 입력의 추적 끊김·잘못된 경로 이탈은 0건, 확인 지점의 보이는 지도 타일 누락은 0건이었습니다. 모든 표본에서 차량 경로 도착과 주차 확인 후 확대·축소를 검사했습니다. 지도 상호·도로명 표시도 저장된 화면으로 검토했습니다. 타일 디코딩 성공이 실제 도로·가게 데이터의 완전성을 증명하지는 않습니다.

## 발견해서 고친 앱 오류

스위스 Furkastrasse에서 약 4.7km가 남았는데 다음 안내 지점이 `arrive`라는 이유로 독일어 “Sie haben Ihr Ziel erreicht…”(목적지에 도착했습니다)가 표시됐습니다. 실제 도착 상태는 아니었으나 안내 문구가 완료형이어서 운전자가 오해할 수 있었습니다.

**공유 Kotlin 추적기를 수정**해 실제 도착 판정 전에는 해당 언어의 중립적인 목적지 표기와 도로명·남은 거리를 사용합니다. 독일어는 `Ziel · Furkastrasse`, 한국어는 `목적지`, 영어는 `Destination`입니다. 실제 도착 확인 뒤에만 기존 완료 문구를 표시합니다. Android와 웹·음성 안내에 같은 수정이 적용되며, 곡선 도로에 무조건 “직진”하라는 문구를 추가하지 않았습니다. 두 번의 유효 도착 위치 확인, GPS 신뢰·주행 잠금·거리 계산은 유지합니다.

- [수정 전 4.7km 안내](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/furka-before.png) · [수정 후 같은 구간](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/furka-after.png)
- [새 실제 브라우저 녹화 MP4](video/CabinOS_ONE_world_furka_v0103.mp4): **18.76초**, 1440×1050, 무음. 합성 GPS를 가속 입력하고 구간별로 지도를 확인하는 실행 녹화이며 사진을 이어 붙인 영상이 아닙니다. 26개 지역 전체 녹화가 아니라 수정 후 스위스 9.34km 재검증 영상입니다.

## 지역별 실제 서버 검사

아래는 수정 전 0.10.2를 사용한 탐색 검사입니다. 이 화면 검토로 도착 문구 오류를 발견했고, 수정 후 0.10.3 검사는 아래에 별도로 기록했습니다. 검색은 경로 목적지와 별개인 지정 상호 검색이며, 경로는 명시한 위도·경도로 설정했습니다. 검색 건수는 첫 페이지 표본(최대 6건)이며 같은 지점·전체 가게 수를 뜻하지 않습니다.

| 지역 | 차량 경로 | 위치 입력 | 지도 확인 | 검색 결과 | 지도 화면 |
|---|---:|---:|---:|---:|---|
| 부산 (KR) | 2.633km | 529 | 8 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/busan/map-detail.png) |
| 대구 (KR) | 1.500km | 304 | 7 | 0 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/daegu/map-detail.png) |
| 대전 (KR) | 5.523km | 1,108 | 7 | 3 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/daejeon/map-detail.png) |
| 제주 (KR) | 4.398km | 883 | 9 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/jeju/map-detail.png) |
| 도쿄 (JP) | 1.217km | 244 | 7 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/tokyo/map-detail.png) |
| 베이징 (CN) | 5.184km | 1,036 | 7 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/beijing/map-detail.png) |
| 싱가포르 (SG) | 4.455km | 896 | 8 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/singapore/map-detail.png) |
| 방콕 (TH) | 4.140km | 828 | 7 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/bangkok/map-detail.png) |
| 뉴델리 (IN) | 5.004km | 1,004 | 9 | 2 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/delhi/map-detail.png) |
| 두바이 (AE) | 3.257km | 656 | 9 | 0 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/dubai/map-detail.png) |
| 이스탄불 (TR) | 5.060km | 1,012 | 7 | 3 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/istanbul/map-detail.png) |
| 런던 (GB) | 2.320km | 464 | 7 | 5 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/london/map-detail.png) |
| 베를린 (DE) | 2.410km | 484 | 7 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/berlin/map-detail.png) |
| 나이로비 (KE) | 2.615km | 528 | 9 | 6 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/nairobi/map-detail.png) |
| 케이프타운 (ZA) | 3.244km | 651 | 8 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/cape-town/map-detail.png) |
| 뉴욕 (US) | 3.657km | 732 | 7 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/new-york/map-detail.png) |
| 토론토 (CA) | 2.114km | 424 | 7 | 0 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/toronto/map-detail.png) |
| 멕시코시티 (MX) | 3.965km | 796 | 8 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/mexico-city/map-detail.png) |
| 상파울루 (BR) | 3.753km | 753 | 8 | 0 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/sao-paulo/map-detail.png) |
| 부에노스아이레스 (AR) | 2.492km | 500 | 7 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/buenos-aires/map-detail.png) |
| 시드니 (AU) | 2.778km | 560 | 7 | 4 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/sydney/map-detail.png) |
| 오클랜드 (NZ) | 2.554km | 513 | 8 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/auckland/map-detail.png) |
| 노르웨이 트롬쇠 (NO) | 5.090km | 1,018 | 10 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/tromso/map-detail.png) |
| 퀸스타운→애로타운 (NZ) | 19.852km | 3,968 | 10 | 0 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/queenstown/map-detail.png) |
| 강원 대관령 주변 (KR) | 12.968km | 2,596 | 7 | 1 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/gangwon/map-detail.png) |
| 스위스 푸르카 산악도로 (CH) | 9.339km | 1,868 | 7 | 0 | [보기](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/docs/validation/world-drive-v0103/furka/map-detail.png) |

## 가게 검색에서 남은 누락

원래 검색어가 0건이었던 지역은 **대구·두바이·토론토·상파울루·스위스 푸르카·뉴질랜드 애로타운**입니다. [원문과 실제 응답](../test-fixtures/validation/world-drive-v0103/baseline.json), [같은 제공자에 대한 수동 대체 검색 진단](../test-fixtures/validation/world-drive-v0103/search-followups.json)을 남겼습니다.

| 원래 검색어 | 추가 조회 결과 | 해석 |
|---|---|---|
| 스타벅스 대구 반월당 | `스타벅스 반월당` 0건, `스타벅스 대구` 6건 | 넓힌 지역 검색이며 반월당 지점을 찾았다는 뜻은 아님 |
| Starbucks Dubai Mall | `Starbucks Dubai` 6건 | 두바이 전체 검색이며 두바이몰 지점 확인 아님 |
| Balzacs Toronto | `Balzac’s`의 ASCII 아포스트로피 표기 `Balzac's Toronto` 1건 | 다른 표기에서 지역·상호 결과 반환 |
| Padaria Bella Paulista Sao Paulo | `Bella Paulista São Paulo` 1건 | 업종 단어를 빼고 현지 표기로 검색한 결과 |
| Hotel Belvedere Furka | `Belvédère Furkapass`도 0건 | 이 제공자로 검색 성공을 확인하지 못함 |
| The Remarkables Sweet Shop Arrowtown | `Remarkables Sweet Shop Arrowtown`도 0건 | 이 제공자로 검색 성공을 확인하지 못함 |

추가 조회는 API 진단으로 수행한 수동 검색입니다. 앱이 이 보정을 자동으로 적용한다는 뜻이 아닙니다. 도시·지점 단어를 임의로 삭제해 다른 가게를 정답으로 선택하지 않았습니다. 검색 결과 0건을 실제 가게의 부재·폐업으로 판단하지 않았고, 전체 카탈로그와의 대조도 하지 못했습니다. 운영용 카카오 등 별도 데이터 제공자 키는 이 환경에 설정돼 있지 않습니다.

## 제공자 데이터와 시험 범위의 한계

- 일본·싱가포르·태국·인도·영국·케냐·남아공·호주·뉴질랜드 표본에서 OSRM `driving_side` 값이 모두 `right`였습니다. 국가의 일반 통행 방향과 맞지 않는 [메타데이터 불일치](../test-fixtures/validation/world-drive-v0103/traffic-side-audit.json)입니다. 현재 앱은 이 필드를 소비하지 않으며 실제 경로 좌표와 회전 정보를 사용합니다. 이번 시험은 차선·통행 법규·일방통행 규정의 독립 검증이 아닙니다.
- 태국어·힌디어 안내는 기존 정책대로 영어로 대체됩니다. 현지어 지도 타일 표시와 현지어 안내 번역·실제 TTS 음성 지원은 별개입니다. 기기의 음성팩·마이크를 통한 실발화도 이번 시험에 포함하지 않았습니다.
- 자동차 도로 끝과 입력 목적지에 차이가 있으면 접근 거리를 표시하고, 100m 초과에서는 확인을 요구합니다. 목적지까지 임의 직선 자동차 경로를 그리지 않았습니다. 인디아게이트 등 도로 밖 목적지·건물 내부·지하층의 실제 진입 가능성은 추가 검증 대상입니다.
- 산악도로의 계절 폐쇄·공사·교통량·실시간 제한은 확인하지 않았습니다. 지도와 경로는 공개 제공자의 관측 시점 데이터이며 항상 접근 가능하다는 보장이 아닙니다.

## 시험 코드에서 바로잡은 부분

1. 부산의 실제 지도 배율은 15였는데 초기 시험이 16으로 가정했습니다. 현재 배율에서 한 단계 확대·축소되는지를 검사하도록 고쳤습니다. 앱 확대 기능의 장애로 보고하지 않습니다.
2. 도착하면 자동 추적을 종료하므로 가속 시험에 도착 50m 전 지도 확인 지점을 추가했습니다. 주행 중에는 중심 추적, 도착 시에는 최종 위치가 보이는지, 주차 뒤에는 정상 확대·축소를 확인합니다.
3. 스위스의 초기 합성 출발 좌표는 차량 도로에서 약 1.25km 떨어져 있었고 앱이 [정상적으로 경로 시작을 거부](../test-fixtures/validation/world-drive-v0103/invalid-origin.json)했습니다. 시험 좌표를 실제 Furkastrasse 위로 수정했습니다. 시드니의 초기 목적지가 항구 수면 위였던 것도 [Circular Quay West 도로 좌표로 수정](../test-fixtures/validation/world-drive-v0103/sydney-coordinate-correction.json)했습니다. 초기 시도는 최종 성공 거리 합계에 중복 집계하지 않았습니다.

## 수정 후 검증·배포

31개 안내 언어와 지역 코드·영어 대체 언어에서 마지막 구간에 완료형 도착 문구가 나오지 않고, 실제 도착 위치 두 번 확인 후에만 도착하는 공유 엔진 회귀 검사를 추가했습니다. **96개 공통 JVM + 62개 Android = 158개 검사**, release 빌드·서명·정렬·lint를 통과했습니다. **브라우저 118개 검사**도 통과했습니다. 회귀 집계는 [별도 기록](../test-fixtures/validation/world-drive-v0103/regressions.json)을 참고하세요.

저장된 26개 실제 경로의 모든 합성 위치를 새 엔진에 다시 입력한 [오프라인 재검증](../test-fixtures/validation/world-drive-v0103/replay.json)은 실제 서버·지도 화면의 새 검증과 구분합니다. 거리 진행이 이전 결과와 일치하는지도 확인했습니다. 스위스는 수정 후 실제 서버·화면으로 한 번 더 실행해 [별도 기록](../test-fixtures/validation/world-drive-v0103/fixed-live.json)과 위 영상을 남겼습니다.

[0.10.3 GitHub Release](https://github.com/hundol047/HYUNDAI/releases/tag/v0.10.3) · [서명 APK](../apk/퓨처랩스_CabinOS_ONE_release.apk) · [웹 ZIP](https://github.com/hundol047/HYUNDAI/raw/refs/tags/v0.10.5/web-release/CabinOS_ONE_web_v0.10.3.zip). 기존 퓨처랩스 인증서를 유지했습니다. APK 변경은 조기 도착 문구 수정이며, 검색 제공자의 미등록 가게를 모두 보완한 버전이 아닙니다. 공개 웹사이트는 여전히 Pages 설정이 필요합니다. [배포 상태](WEB_DEMO.md).

## 재현

```bash
./scripts/build-web.sh
python3 scripts/serve-web.py --directory build/site
# 별도 터미널, 실제 공개 서버에 순차 요청
node scripts/web/verify-world-drive-live.cjs
# 특정 지역만 실제 서버 재검사
CABIN_WORLD_CASES=furka CABIN_WORLD_OUT=artifacts/world-drive-check node scripts/web/verify-world-drive-live.cjs
# 저장된 공개 근거만 사용한 오프라인 엔진 재검증
CABIN_WORLD_BASELINE=docs/validation/world-drive-v0103/baseline.json node scripts/web/verify-world-route-replay.cjs
```

라이브 검사는 기본 CI에 포함하지 않았습니다. 각 경로 전체에 합성 1Hz·5m/s 위치를 입력하고, 지도는 25% 간격·주요 분기·도착 직전/도착·정차 확대 지점에서 확인합니다. 모든 중간 프레임을 렌더하거나 모든 옆 도로를 수집한 것은 아닙니다. 화면에 필요한 정상 타일만 요청하며 오프라인 지도를 미리 내려받지 않습니다. 202곳의 대기 시간에는 화면 캡처 비용도 들어 있어 순수 네트워크 지연이나 실기기 FPS로 해석하면 안 됩니다.
