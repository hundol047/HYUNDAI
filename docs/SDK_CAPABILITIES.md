# Pleos SDK 핵심 기능 및 검증 범위

공식 Maven의 **Vehicle 2.3.2 / NaviHelper 3.0.5 AAR로 컴파일·debug/release 빌드·JVM 테스트·lint를 실행**했습니다. SDK 클래스 대체물은 없습니다. 장치 실행·영상의 최신 상태와 APK 식별자는 [제출 검토표](SUBMISSION_REVIEW_KO.md)를 따릅니다. JVM 테스트의 합성 입력은 실제 차량 응답의 증거가 아닙니다.

## 기획서 5번에 사용할 기능/API 매핑

| 핵심 기능 | 실제 API·입력 | 제품 동작과 실패 처리 |
|---|---|---|
| 정차 전용 기능의 안전 제한 | AAOS `CarPropertyManager`, `PERF_VEHICLE_SPEED`, `GEAR_SELECTION` | 유효한 속도 0 + P를 1.5초 확인한 뒤 집중·휴식·설정 허용. D/0도 잠금. 연결·권한·신호 만료 시 잠금. GPS나 Brake로 대체하지 않음 |
| 충전 후 활동 자동 종료 | `Vehicle.getEvBattery()` → `checkEvBatteryStateOfChargeCapabilities` → `getEvBatteryStateOfCharge` | 3초 이내 SDK SOC(0~100%)를 1차 입력으로 사용. 사용자가 선택한 80/90/100% 계획 목표에 도달하면 활동 예산 0, 집중 세션 종료, 출발 준비 안내. 차량 충전 목표를 변경하는 Set API는 호출하지 않음 |
| 충전 계획 유효성 | `checkEvChargeSwitchCapabilities` → `getEvChargeSwitchState` + AAOS `EV_CHARGE_STATE` | SDK 충전 스위치 OFF면 충전 중 예산을 제거. ON만으로 실제 전력 유입을 단정하지 않고 AAOS 충전 상태와 함께 사용. SOC·충전·Brake 각각 독립적으로 조회/오류 처리 |
| 실제 충전 잔여 시간 기반 계획 | AAOS `EV_CHARGE_TIME_REMAINING`(초) | 차량이 제공한 잔여 시간으로 출발 마감 계산. 수동 계획과 더 이른 마감 선택. 미지원/만료 값으로 예상 분을 만들지 않음. **Vehicle 2.3.2 EvBattery에 잔여 시간 API는 없어 AAOS와 조합** |
| 도착 목표에 맞춘 출발·집중 마감 | `NaviHelper.getDestinationInfo()` → `NaviHelperEventListener.onDestinationInfo(DestinationInfo)`의 `remainTime`(초), `remainDistance`(m) | 사용자가 정한 도착 목표에서 남은 주행 시간을 빼 출발 마감 계산, 준비·왕복 시간을 더 빼 집중·주변 활동 예산에 반영. 예: 60분 뒤 도착, 주행 45분, 준비 5분 → 활동 10분. 거리는 목적지 정보 표시·유효성 검사에 사용하며 속도로 임의 ETA를 추정하지 않음 |
| 내비 정보 소실 처리 | 목적지 변경·취소·종료·오류 콜백, 2초 조회/10초 만료 | 목적지/ETA 표시를 지우고 이전 마감을 보수적으로 유지해 세션이 자동 연장되지 않도록 함. 사용자가 도착 목표를 다시 설정/해제해 재계획. 요청을 보냈다는 이유만으로 경로 성공 표시하지 않음 |
| 정차 중 목적지·주변 충전소 검색 | `requestNavigationScreen`: `SearchPlace`, `NearbyChargingStations`, `RecentDestinations`, `Foreground` | P·정지 확인 후 차량 기본 내비게이션으로 전환. 검색 결과·도로·교통·주행 안내는 설치된 내비 서비스의 지원 범위. CabinOS에서 기본 내비의 가게 목록을 임의 재정렬했다고 주장하지 않음 |

**구현 설명(장치 전체 검증 후 기획서에 확정):** CabinOS ONE은 Pleos EvBattery의 실제 SOC를 활동 종료 조건으로, NaviHelper의 목적지 잔여 시간을 출발 계획의 입력으로 사용한다. StopSense의 활동 가능 시간과 CabinOS 집중·휴식 세션이 하나의 시간표를 공유해 차량 상태와 다음 일정에 맞춰 함께 조정된다. 충전 잔여 시간과 안전한 정차 확인은 AAOS 공개 속성을 조합한다.

Brake의 `checkBrakePedalStatusCapabilities` / `getCurrentBrakePedalStatus`는 진단 표시입니다. 핵심 기능·정차 판정·필수 영상 통과 조건에 포함하지 않습니다. Capability가 READ/READ_WRITE일 때만 Get을 호출하고, null·오류·시간 초과를 성공 값으로 바꾸지 않습니다.

## 권한·지원 프로필

- AAOS: `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_ENERGY`, `CAR_INFO`.
- Pleos: `CAR_ENERGY`(EvBattery), `NAVI_ROUTE`(목적지), `NAVI_CUSTOM_MAP`(화면 요청), `CAR_DRIVE`(Brake 진단). 모두 `pleos.car.permission.*` 접두사를 사용합니다.
- `-PpleosSdk=true` 빌드에서만 공식 SDK 포함. 기본 AAOS 개발 프로필은 SDK 미포함을 명시합니다. 제출 APK는 SDK 포함 프로필이어야 합니다.
- Automotive에서는 실제 차량 모드 강제·앱 내부 데모 조작판 숨김. 일반 Android의 시뮬레이터는 시연용입니다.
- **정차·충전 전용. DO 미선언.** 출발 후 기본 내비게이션에서 길 안내를 이어갑니다. 세로 1칸/가로 창을 위한 반응형 UI이며 실제 Pleos 분할 창 검증 여부는 별도로 기록합니다.
- Pleos STT/TTS/FusedLocation/Vehicle Control은 연결하지 않았습니다. 음성·위치는 Android 플랫폼 서비스이며 기기 엔진·언어팩·권한이 필요합니다. 차량 쓰기·공조·조명 API는 사용하지 않습니다.
- 일반 Android/웹 지도는 OSM·공용 서비스 기반 데모입니다. 모든 국가·도로·가게의 완전성이나 상용 SLA를 보장하지 않습니다. [데이터·라이선스](THIRD_PARTY.md).

## 공식 근거

- [SDK 호환성·AAOS 공개 API](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)
- [Vehicle 설정 및 공식 Maven](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [EvBattery SOC](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-battery-state-of-charge)
- [EvBattery 충전 스위치](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/ev-battery/get-ev-charge-switch-state)
- [NaviHelper 목적지 요청](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/get-destination-info)
- [DestinationInfo 단위·필드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/destination-info/entry)
- [Pleos Connect Emulator 2.1.0/API 34](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

공식 문서의 Dokka 본문과 실제 AAR 시그니처를 대조했습니다. `RouteStateInfo`에 없는 ETA 필드를 만들지 않았으며 `DestinationInfo`를 사용합니다. 최종 OEM 인증·공모전 승인과는 별개입니다.

### 0.11.1 선택적 실내 제안 (실제 에뮬레이터 미검증)

| 기능 | SDK 도메인·메서드 | 읽기 권한 | 공식 문서 | 에뮬레이터 검증 |
|---|---|---|---|---|
| 동승자 감지 → 함께 휴식·오디오 추천 | Seat.checkSeatOccupancyCapabilities / getCurrentSeatOccupancy | pleos.car.permission.CAR_SEATS | [좌석 점유](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/get-current-seat-occupancy) | 아니요 |

공식 Vehicle 2.3.2 AAR의 실제 메서드와 권한 주석을 확인했습니다. capability가 READ/READ_WRITE인
단일 좌석 영역만 읽습니다. 뒷좌석 점유 또는 서로 다른 좌석 2개 이상 점유일 때 동승자를 표시합니다.
운전석 좌우를 알려 주는 API는 확인하지 못했으므로 앞좌석 1개만 점유하면 동승자로 단정하지 않습니다.
벨트 체결만으로 점유를 추정하지 않습니다. 미지원·null·3초 시간 초과는 숨기며 백그라운드에서 구독을 중단합니다.
이 정보는 정차 허용 판정에 사용하지 않으며 차량 제어 API를 호출하지 않습니다.

| 기능 | SDK 도메인·메서드 | 읽기 권한 | 공식 문서 | 에뮬레이터 검증 |
|---|---|---|---|---|
| 휴식 전 문 확인 | Door.checkDoorPositionCapabilities / getCurrentDoorPosition | pleos.car.permission.CAR_DOORS | [Door](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/get-current-door-position) | 아니요 |
| 휴식 전 창문 확인 | Window.checkWindowPositionCapabilities / getCurrentWindowPosition | pleos.car.permission.CAR_WINDOWS | [Window](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/window/get-current-window-position) | 아니요 |
| 실내 온도·공조 상태 | HVAC.checkHvacTemperatureCurrentCapabilities / getHvacTemperatureCurrent(CELSIUS), checkHvacPowerCapabilities / getHvacPowerState | pleos.car.permission.CAR_CLIMATE | [온도](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-temperature-current), [공조](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/get-hvac-power-state) | 아니요 |

문·창문은 0을 포함하는 capability 범위 내 위치만 사용합니다. 0 이외의 위치를 열림으로 표시하며,
실제 기기의 영역별 값과 방향은 시연에서 확인해야 합니다. 온도는 명시적으로 섭씨로 요청합니다.
모든 안내는 읽기 전용이며 장치 동작을 바꾸지 않습니다.

| 기능 | SDK 도메인·메서드 | 읽기 권한 | 공식 문서 | 에뮬레이터 검증 |
|---|---|---|---|---|
| 워셔액 점검 카드 | Safety.checkLowWasherFluidWarningStateCapabilities / hasLowWasherFluidWarning | pleos.car.permission.CAR_SAFETY | [워셔액](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/has-low-washer-fluid-warning) | 아니요 |
| 표시등 확인: 사용 제외 | Safety.checkTelltalesCapabilities / getTelltalesInfo | capability: pleos.car.permission.PRIVATE_CAR_SAFETY / getter: CAR_SAFETY | [표시등](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/get-telltales-info) | 아니요 |

공식 AAR에서 표시등 getter는 CAR_SAFETY를 요구하지만 capability 메서드는 PRIVATE_CAR_SAFETY를 요구합니다.
SDK 포함 lint에서 이를 확인했습니다. 일반 공모전 앱에 비공개 권한이 허용된다는 근거가 없어 표시등·TPMS 읽기를 제외했습니다.
capability 확인을 우회하거나 비공개 권한을 요청하지 않습니다.
Tire.getLowTirePressure는 공식 2.3.2 AAR에서 Float 반환이며 Boolean 저압 경고 API가 아닙니다.
따라서 독자 저압 플래그는 구현하지 않았고 임계값·단위도 추정하지 않습니다.
[해당 공식 메서드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/tire/get-low-tire-pressure).
TPMS 표시등도 현재 구현에서 사용하지 않습니다.
