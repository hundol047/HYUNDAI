# 지도 구성요소와 출처

- **osmdroid Android 6.1.20** — Apache License 2.0. 출처: https://github.com/osmdroid/osmdroid . 라이선스 전문은 APK의 `assets/licenses/osmdroid-Apache-2.0.txt`에 포함합니다.
- **Leaflet 1.9.4** — BSD 2-Clause. 출처: https://leafletjs.com/ . 웹 배포물의 `vendor/leaflet/LICENSE`에 원문을 포함합니다.
- **OpenStreetMap 데이터** — © OpenStreetMap contributors, Open Database License. https://www.openstreetmap.org/copyright . 화면에 출처를 표시합니다.
- **OSM 타일 서비스 정책** — https://operations.osmfoundation.org/policies/tiles/ . 대량 다운로드·프리페치를 구현하지 않았으며 Android는 의미 있는 앱 User-Agent와 동시 요청 제한을 사용합니다. 서비스의 실제 사용 가능 여부와 정책 준수를 운영 전 추가 확인해야 합니다.
- **Overpass API** — https://overpass-api.de/ . 사용자 요청 시 주변 공개 POI를 조회합니다. 이 서비스는 앱 자체의 서버가 아니며, 위치 기준점이 요청에 포함됩니다.

프로젝트의 판교 예시 장소 이름과 핀은 시연용 데이터이며 실제 매장 목록이나 실제 API 응답을 복제한 것이 아닙니다. 실제 OSM 조회 결과와 구분합니다.

- **Nominatim** — explicit worldwide destination search; OpenStreetMap data attribution applies. Public service policy: https://operations.osmfoundation.org/policies/nominatim/ . No autocomplete/systematic enumeration; queries are cached in memory and throttled. Replace with a self-hosted/contract service before scaled deployment.
- **OSRM** — driving-route geometry and maneuvers from the public demonstration service https://router.project-osrm.org/ . Project: https://project-osrm.org/ . No OSRM binary/source is embedded in the APK. Public demo has no application SLA; compatible HTTPS endpoint is configurable. OSM roads and access restrictions vary by region.

Deterministic UI fixtures use synthetic protocol/GPS data; live-recorded route/search fixtures under app/src/test/resources/live-navigation and shop-live are explicitly recorded OSM/OSRM responses (see their README files), replayed without live CI requests. The 0.5 video uses live provider traffic with synthetic GPS and offline native gallery renders, not actual road driving.

- **선택적 Kakao Local 서버 어댑터** — https://developers.kakao.com/docs/latest/ko/local/dev-guide . 키워드 검색의 제공자 이름/주소/전화/상세 링크를 변환하며 공급자 출처를 표시합니다. SDK/키를 APK에 포함하지 않습니다. 실서버 키와 최신 약관/할당량 확인은 운영자 설정이 필요하며 현재 실서버 미검증입니다.
- **Google Maps 외부 검색 URL** — https://www.google.com/maps/search/?api=1 . 외부 페이지 링크만 열며 Google Places 데이터/SDK를 앱에 포함하지 않습니다.

## 앱의 주요 오픈소스 의존성

- **Kotlin / kotlinx.coroutines / kotlinx.serialization** — Apache-2.0. https://github.com/JetBrains/kotlin , https://github.com/Kotlin/kotlinx.coroutines , https://github.com/Kotlin/kotlinx.serialization . Kotlin 컴파일러·표준 라이브러리와 코루틴을 사용합니다.
- **AndroidX (Activity, Core, Lifecycle, Navigation, Compose, Room)** — Apache-2.0. https://android.googlesource.com/platform/frameworks/support/ . UI·수명 관리·로컬 DB를 사용합니다.
- **Dagger / Hilt** — Apache-2.0. https://github.com/google/dagger . 의존성 주입에 사용합니다.
- **osrm-text-instructions** — BSD-2-Clause. `third_party/osrm-text-instructions/LICENSE.txt` 및 출처 README를 포함합니다. 길 안내 번역 생성에 사용합니다.
- **JUnit / Robolectric / Playwright** — 테스트·개발 도구이며 APK 런타임 구성요소와 구분합니다. JUnit 4는 EPL-1.0, Robolectric과 Playwright는 Apache-2.0입니다.
- **Pleos Vehicle 2.3.2 / NaviHelper 3.0.5** — 선택 프로필의 제공자 SDK입니다. Pleos Playground 이용 조건·배포 조건이 적용되며 오픈소스 Apache/BSD 라이선스로 재허가하지 않습니다. 공식 AAR(Vehicle, NaviHelper 및 vehicle/navi data·aidl 전이 의존성) 내부의 LICENSE/NOTICE 이름 항목은 없음을 확인했습니다. 이것이 제공자 배포 조건의 면제를 의미하지 않습니다.

Apache-2.0 원문과 APK 구성요소 목록은 `app/src/main/assets/licenses/`에 포함합니다. 웹 Leaflet 및 번역 라이선스는 각 배포 자산의 원문을 유지합니다. 데이터 출처 표시는 앱 지도에서도 유지합니다.

- **Gson** — Apache-2.0. https://github.com/google/gson . 공식 Vehicle SDK의 전이 런타임 의존성입니다.

- **Photon 장소 검색 API** — https://photon.komoot.io/ ; API 명세: https://github.com/komoot/photon/blob/master/docs/api-v1.md . Android 주변 장소 검색은 `/reverse`의 `osm_tag`, `radius=2`, `limit=15`를 사용합니다. OSM 데이터 출처를 화면에 표시하며 기준점 좌표를 전송합니다. 분류별 요청 사이 1.1초 간격과 같은 지역의 2분 메모리 캐시를 적용합니다. 공개 데모의 합리적 사용 범위·가용성에는 제한이 있으므로 대규모 운영 시 자체/계약 서비스를 준비해야 합니다. Photon 서버 소스/바이너리를 APK에 포함하지 않습니다.

- **장소 사진 연결** — 검색 결과의 공개 OSM 장소 ID로 OpenStreetMap 편집 API의 해당 객체 태그를 읽고, 그 객체에 등록된 Commons 파일 또는 Wikidata `P18` 사진을 조회합니다. 브랜드용 Wikidata ID는 사용하지 않으며 장소 Wikidata 좌표가 기준점 300m 이내인지 확인합니다. 사진은 Wikimedia Commons의 저작자·라이선스 정보와 원문 링크를 함께 표시합니다. 원본 장소 사진이 없는 경우 임의로 다른 매장 사진을 붙이지 않습니다. 보이는 항목만 조회하며 동시 조회 2개·메모리 캐시를 적용합니다. 서버에 공개 장소/파일 식별자가 전달됩니다. https://wiki.openstreetmap.org/wiki/API_v0.6 · https://www.wikidata.org/wiki/Wikidata:Data_access · https://commons.wikimedia.org/
