# SDK 및 권한 확인 보고서

공식 문서 확인을 시도한 주소:

- https://document.pleos.ai/
- https://pleos.ai/
- https://appdevchallenge.auto-hmg.io/guide/development
- 제출 안내 https://appdevchallenge.auto-hmg.io/guide/submission (이전 대화에서 접근 시도)

현재 클라우드 네트워크 프록시가 모두 403을 반환했습니다. 따라서 Pleos SDK의 저장소 주소·버전·신호·권한·메서드를 확인했다고 주장하지 않습니다. 비공개 SDK를 임의로 생성하거나 Android의 일반 API를 Pleos API로 표기하지 않았습니다. 공모전 호환성·제출 요건 검증은 완료되지 않았습니다.

| 기능 | 구현 | 실제/데모 및 제약 |
|---|---|---|
| 차량 속도·P 기어 | VehicleDataProvider, SimulationVehicleProvider | 명시적인 데모 데이터. 실차 모드는 미연결/UNKNOWN |
| 충전 상태·잔여 시간 | ChargingProvider | 데모 추정치 또는 사용자 대기 시간 입력 |
| Pleos Vehicle SDK | 추상화 인터페이스 | 미연동. 공식 SDK 확인 필요 |
| Pleos FusedLocation | LocationProvider | Pleos 미연동. Android LocationManager 어댑터 제공 |
| Pleos NaviHelper | NavigationProvider | Pleos 미연동. 실차 소스·권한·60초 이내 실제 위치일 때만 일반 지도 앱 intent 지원 |
| 경로·위험 이벤트·주변 장소 | 로컬 저장소, 점수·시간 계산 | 전부 시뮬레이션. 실제 도로 검출/영업시간 정보 아님 |
| 한국어 TTS | android.speech.tts.TextToSpeech | 실제 플랫폼 어댑터. 기기 엔진/한국어 언어팩 필요 |
| 한국어 STT | android.speech.SpeechRecognizer | 실제 플랫폼 어댑터. 권한과 인식 서비스 필요. 미지원 시 명령 버튼 사용 |
| 위치 | android.location.LocationManager | 권한 부족 시 실패 처리. 데모 위치 거부 토글과 실제 권한은 별개 |
| 차량 공조·조명 | 없음 | 지원 안 함을 설정 화면에 표시 |
| 생성형 AI | 없음 | 로컬 규칙 기반 한국어 명령 해석을 사용 |
| 위험 이벤트 공유 | 로컬 동의 설정 + 최소 이벤트 모델 | 서버 및 실제 업로드 미구현. 외부 전송 없음 |

Android 권한은 `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`입니다. 마이크 권한은 정차 상태에서만 요청하며 주행 중 새 시스템 권한 창을 열지 않습니다. 시뮬레이터의 실차 연결 화면에서 위치 권한을 요청할 수 있지만 위치만으로 주차 상태를 인정하지 않습니다. 설정에서 Android 13+ 알림 권한을 요청할 수 있습니다. OS 알림은 저장된 계획을 알리는 비정확 알람이며 차량 상태를 확인하거나 정차 기능을 실행하지 않습니다. 차량용 특권 권한을 추측하여 선언하지 않았습니다. `android.hardware.type.automotive`는 optional이며 일반 Android 에뮬레이터에서도 동일 앱의 데모를 실행할 수 있습니다.

이 앱은 OEM 또는 차량 앱 마켓의 심사를 통과한 제품이 아닙니다. AAOS 실차 신호, OEM distraction 정책, 차량별 권한, Pleos 지원 버전은 공식 문서와 실제 장치에서 추가 검증해야 합니다. 데모 시뮬레이터 제어는 개발 시연용으로 포함되며 실제 차량 운행 환경에서는 제공하면 안 됩니다.

공식 문서 접근이 복구되면 해당 SDK를 별도 어댑터로 추가하고 동일 core 상태 엔진·안전 테스트를 유지해야 합니다. 일반 안드로이드 데모 APK의 동작만으로 실차 연동이나 공모전 인증을 주장해서는 안 됩니다.
