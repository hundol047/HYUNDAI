# SDK 및 권한 확인 보고서

2026-10-24 재확인: 공식 문서는 HTTP 200으로 읽었습니다. 이번 변경에서 AAOS 속도·기어·배터리·충전 읽기와 선택 빌드의 Pleos Vehicle SDK Brake/EvBattery 조회, NaviHelper 화면 연결 코드를 추가했습니다. **공식 SDK AAR와 Pleos 에뮬레이터 이미지 다운로드는 `nexus-playground.pleos.ai` 프록시 CONNECT 403으로 차단되어, SDK 포함 빌드·Pleos 실행·시연 영상은 아직 미검증입니다.** [구현·빌드 프로필·남은 검증](PLEOS_VEHICLE_STATE_KO.md).

공개 main의 기존 0.10.5와 웹 영상에는 이번 어댑터가 없습니다. 작업 브랜치의 0.11.0-rc1 후보 APK는 AAOS 어댑터만 포함하며, 공식 SDK 포함 빌드는 `-PpleosSdk=true`를 명시합니다.

- [공모전 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)
- [공모전 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [Pleos Connect Emulator 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [프로젝트 생성 및 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [SDK Overview](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)
- [Vehicle SDK Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)

확인된 요구사항은 minSdk API 28 이상, Gradle 8.0 이상, Java/Kotlin입니다. 현재 앱의 minSdk 29·Gradle 8.11.1은 이 최소 요구사항을 충족합니다. 공식 에뮬레이터 가이드는 Pleos Connect Emulator 2.1.0/API 34, Vehicle 소개는 SDK 2.3.2·Vehicle Service 2.3.21 기준입니다. 현재 앱을 해당 Pleos 환경에서 실행 검증한 것은 아닙니다.

공식 절차는 Playground 가입과 프로젝트 생성을 안내합니다. Vehicle State·NaviHelper는 신청 없이 사용 가능한 상태, FusedLocation·TextToSpeech·SpeechToText·Vehicle Control은 준비 중으로 안내됩니다. 권한·지원 여부는 실제 프로젝트와 최신 공식 안내에서 확인해야 합니다. 이 작업에서는 계정 생성·약관 동의·프로젝트 등록을 수행하지 않았으며, 비공개 자격 증명을 만들거나 추측하지 않았습니다.

| 기능 | 구현 | 실제/데모 및 제약 |
|---|---|---|
| 차량 속도·P 기어 | VehicleStateProvider → AndroidCarPropertyTransport → CarPropertyManager | AAOS 공개 API로 PERF_VEHICLE_SPEED / GEAR_SELECTION 구독·읽기 구현. 기본 빌드 컴파일/합성 입력/자동차 UI 테스트 통과, Pleos 장치에서 수신은 미검증. 일반 기기 데모는 별도 |
| 충전 상태·잔여 시간 | AAOS EV_CHARGE_STATE / EV_CHARGE_TIME_REMAINING | 지원 속성의 실제 값을 읽는 어댑터. 미지원/만료면 없음. 수동 대기 시간 별도. 실제 장치 수신은 미검증 |
| 배터리 | AAOS EV_BATTERY_LEVEL / INFO_EV_BATTERY_CAPACITY, Pleos EvBattery | Wh/Wh→% 또는 SDK getEvBatteryStateOfCharge 정수 %. capability·범위·만료 검사. SDK 포함 프로필 미컴파일 |
| Pleos Vehicle SDK | `src/pleos` typed Vehicle 2.3.2 어댑터 | `initialize` → Brake/EvBattery capability 확인 → 비동기 Get → `release` 구현. `-PpleosSdk=true` 필요. 공식 AAR 다운로드 차단으로 해당 프로필 컴파일·서비스 응답 미검증. 기본 빌드는 미포함을 표시 |
| Pleos FusedLocation | LocationProvider | Pleos 미연동. Android LocationManager 어댑터 제공 |
| EchoRoad 내비게이션 | Nominatim · OSRM · Android GPS · 공통 NavigationTracker | 국가 제한 없는 목적지 검색/자동차 경로/회전 안내/재탐색 구현. 서울·파리·뉴욕·도쿄 실서버와 서울 브라우저 연결 확인. 실기기·실도로 미검증. [범위](NAVIGATION_KO.md) |
| Pleos NaviHelper | `src/pleos` NaviHelper 3.0.5 어댑터 | 연결 콜백·오류 리스너·해제, SearchPlace/Foreground/NearbyChargingStations/RecentDestinations 요청. 차량 UI에서 사용. SDK 포함 프로필 컴파일·서비스 실행 미검증 |
| 위험 이벤트·주행 데모 경로 | 로컬 저장소, 점수·시간 계산 | 시뮬레이션. 실제 도로 검출 아님. GPS 내비게이션에는 예시 위험을 표시하지 않음 |
| 정차용 주변 지도·장소 | osmdroid/Leaflet + OSM 타일 + Overpass | 실제 서비스 연결 어댑터. OSM 타일 실제 연결 확인, 0.10.5 Overpass 실서버 3개 도시 응답 확인. 초기 목록은 예시. 도보 추정·영업 여부 미확인 |
| 한국어 TTS | android.speech.tts.TextToSpeech | 실제 플랫폼 어댑터. 기기 엔진/한국어 언어팩 필요 |
| 한국어 STT | android.speech.SpeechRecognizer | 실제 플랫폼 어댑터. 권한과 인식 서비스 필요. 미지원 시 명령 버튼 사용 |
| 위치 | android.location.LocationManager | 권한 부족 시 실패 처리. 데모 위치 거부 토글과 실제 권한은 별개 |
| 차량 공조·조명 | 없음 | 지원 안 함을 설정 화면에 표시 |
| 생성형 AI | 없음 | 로컬 규칙 기반 한국어 명령 해석을 사용 |
| 위험 이벤트 공유 | 로컬 동의 설정 + 최소 이벤트 모델 | 서버 및 실제 업로드 미구현. 외부 전송 없음 |

AAOS 에너지 읽기용 `android.car.permission.CAR_ENERGY`, `android.car.permission.CAR_INFO`, SDK EvBattery용 `pleos.car.permission.CAR_ENERGY`와 내비 화면용 `pleos.car.permission.NAVI_CUSTOM_MAP`도 선언합니다. 새 차량 읽기 권한은 `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN`, `pleos.car.permission.CAR_DRIVE`입니다. 앞의 두 권한은 AAOS 속도/기어, 마지막 권한은 Pleos Brake 읽기에 사용합니다. 차량 제어 권한이나 Set API는 추가하지 않았습니다.

기존 Android 권한은 `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`입니다. 마이크 권한은 정차 상태에서만 요청하며 주행 중 새 시스템 권한 창을 열지 않습니다. 지도 화면의 내 위치 사용 또는 시뮬레이터의 실차 연결 화면에서 위치 권한을 요청할 수 있지만 위치만으로 주차 상태를 인정하지 않습니다. 설정에서 Android 13+ 알림 권한을 요청할 수 있습니다. OS 알림은 저장된 계획을 알리는 비정확 알람이며 차량 상태를 확인하거나 정차 기능을 실행하지 않습니다. 차량 속도 읽기 권한이 허용되지 않으면 UNKNOWN을 유지합니다. 에뮬레이터 설치 및 권한 확인 절차는 별도 가이드를 참조하세요. `android.hardware.type.automotive`는 optional이며 일반 Android 에뮬레이터에서도 동일 앱의 데모를 실행할 수 있습니다.

이 앱은 OEM 또는 차량 앱 마켓의 심사를 통과한 제품이 아닙니다. AAOS 실차 신호, OEM distraction 정책, 차량별 권한, Pleos 지원 버전은 공식 문서와 실제 장치에서 추가 검증해야 합니다. Automotive 기능이 있는 기기에서는 실제 차량 모드로 시작하고 시뮬레이터 제어를 숨기며 ViewModel에서도 데모 전환을 거부합니다. 일반 Android 기기에는 데모를 유지합니다.

현재 남은 선행 조건은 공식 SDK/이미지 다운로드 서버 접근입니다. 허용 도메인을 클라우드 설정 초안에 추가했지만 게시·런타임 적용은 확인되지 않았습니다. 일반 Android 테스트와 AAOS 코드 빌드 성공을 Pleos 실기기 연동이나 공모전 인증으로 표기하지 않습니다.


0.7.0의 가게명·지점·주소 검색, 제공되는 전화/등록 영업시간, 20km·더 보기·지도 핀, 실제 표본/오류와 선택적 카카오 서버 범위는 [가게 검색 안내](PLACE_SEARCH_KO.md)에 있습니다. 모든 도로/가게를 보장하지 않으며 카카오 실서버는 키 미등록으로 미검증입니다. Google Maps는 외부 검색 링크이며 Google Places API 연동은 아닙니다.


0.9.0 GPS 상태/재연결과 실제 제공자 연결·미검증 항목은 [GPS 재점검](GPS_REVIEW_KO.md)에 별도 기록합니다.

## 정차 구성 및 화면 대응

차량 모드에서는 집중·휴식·출발 계획을 정차 중에 사용하고, 기본 내비게이션으로 길 안내를 넘깁니다. DO를 선언하지 않으므로 자체 EchoRoad 화면을 주행 중 사용할 수 있다고 주장하지 않습니다. 일반 Android/웹의 EchoRoad 데모·공용 지도와 차량 모드의 제품 범위는 다릅니다.

가로 방향 고정을 제거하고 resizeableActivity를 선언했습니다. 360×800, 600×800, 1280×800 Compose 조작 테스트는 실제 UI 렌더링이지만 Pleos 분할 창 실기기 검증을 대신하지 않습니다. [검토 결과](SUBMISSION_REVIEW_KO.md).
