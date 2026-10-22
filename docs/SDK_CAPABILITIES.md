# SDK 및 권한 확인 보고서

2026-10-21 환경 재시작 후 다음 공식 문서를 HTTP 200으로 확인했습니다. 초기 작업 당시의 프록시 403 문제와 현재 상태를 구분합니다. 문서 확인이 앱의 SDK 연동 완료를 의미하지는 않습니다.

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
| 차량 속도·P 기어 | VehicleDataProvider, SimulationVehicleProvider | 명시적인 데모 데이터. 실차 모드는 미연결/UNKNOWN |
| 충전 상태·잔여 시간 | ChargingProvider | 데모 추정치 또는 사용자 대기 시간 입력 |
| Pleos Vehicle SDK | 추상화 인터페이스 | 공식 문서 확인, 앱 어댑터 미연동 |
| Pleos FusedLocation | LocationProvider | Pleos 미연동. Android LocationManager 어댑터 제공 |
| EchoRoad 내비게이션 | Nominatim · OSRM · Android GPS · 공통 NavigationTracker | 국가 제한 없는 목적지 검색/자동차 경로/회전 안내/재탐색 구현. 실서버·실도로 미검증. [범위](NAVIGATION_KO.md) |
| Pleos NaviHelper | 기존 NavigationProvider | Pleos 미연동. 별도 프레임워크 GPS 내비게이션은 위 행 참조 |
| 위험 이벤트·주행 데모 경로 | 로컬 저장소, 점수·시간 계산 | 시뮬레이션. 실제 도로 검출 아님. GPS 내비게이션에는 예시 위험을 표시하지 않음 |
| 정차용 주변 지도·장소 | osmdroid/Leaflet + OSM 타일 + Overpass | 실제 서비스 연결 어댑터. 클라우드 실서버 접속 미검증, 초기 목록은 예시. 도보 추정·영업 여부 미확인 |
| 한국어 TTS | android.speech.tts.TextToSpeech | 실제 플랫폼 어댑터. 기기 엔진/한국어 언어팩 필요 |
| 한국어 STT | android.speech.SpeechRecognizer | 실제 플랫폼 어댑터. 권한과 인식 서비스 필요. 미지원 시 명령 버튼 사용 |
| 위치 | android.location.LocationManager | 권한 부족 시 실패 처리. 데모 위치 거부 토글과 실제 권한은 별개 |
| 차량 공조·조명 | 없음 | 지원 안 함을 설정 화면에 표시 |
| 생성형 AI | 없음 | 로컬 규칙 기반 한국어 명령 해석을 사용 |
| 위험 이벤트 공유 | 로컬 동의 설정 + 최소 이벤트 모델 | 서버 및 실제 업로드 미구현. 외부 전송 없음 |

Android 권한은 `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`입니다. 마이크 권한은 정차 상태에서만 요청하며 주행 중 새 시스템 권한 창을 열지 않습니다. 지도 화면의 내 위치 사용 또는 시뮬레이터의 실차 연결 화면에서 위치 권한을 요청할 수 있지만 위치만으로 주차 상태를 인정하지 않습니다. 설정에서 Android 13+ 알림 권한을 요청할 수 있습니다. OS 알림은 저장된 계획을 알리는 비정확 알람이며 차량 상태를 확인하거나 정차 기능을 실행하지 않습니다. 차량용 특권 권한을 추측하여 선언하지 않았습니다. `android.hardware.type.automotive`는 optional이며 일반 Android 에뮬레이터에서도 동일 앱의 데모를 실행할 수 있습니다.

이 앱은 OEM 또는 차량 앱 마켓의 심사를 통과한 제품이 아닙니다. AAOS 실차 신호, OEM distraction 정책, 차량별 권한, Pleos 지원 버전은 공식 문서와 실제 장치에서 추가 검증해야 합니다. 데모 시뮬레이터 제어는 개발 시연용으로 포함되며 실제 차량 운행 환경에서는 제공하면 안 됩니다.

공식 문서 접근이 복구되면 해당 SDK를 별도 어댑터로 추가하고 동일 core 상태 엔진·안전 테스트를 유지해야 합니다. 일반 안드로이드 데모 APK의 동작만으로 실차 연동이나 공모전 인증을 주장해서는 안 됩니다.
