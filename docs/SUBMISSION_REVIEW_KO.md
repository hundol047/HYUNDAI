# 제출 검토표 — 0.11.0-rc1

2026-10-24. 이 문서는 소스 변경과 실제로 검증한 범위를 구분합니다. **Pleos 공식 AAR를 포함한 컴파일·공식 에뮬레이터 시연은 미완료**입니다. 코드가 있다는 사실만으로 SDK 연동 성공을 주장하지 않습니다.

## 지적 사항별 처리

| 항목 | 조치 | 남은 조건 |
|---|---|---|
| 1. 차량 코드 없음 | AAOS `CarPropertyManager`로 속도·기어, 배터리 Wh/용량, 충전 상태·잔여 초 읽기. optional `android.car`, 읽기 권한, foreground 수명 관리. Pleos EvBattery capability→Get 및 NaviHelper 기본 내비 화면 요청 어댑터 추가 | 실제 Pleos AAR 컴파일·차량 응답 검증 |
| 2. 데모 조작판 | Automotive 기기에서 데모 선택·주입·전체 시나리오를 UI와 ViewModel에서 차단. 일반 기기의 실차 모드에서도 조작판 숨김 | 공식 에뮬레이터 최종 확인 |
| 3. 가로 고정 | landscape 제거, resizeableActivity 선언. 좁은 창의 대시보드·세션·설정은 한 열, 지도는 위아래 배치 | OEM의 실제 분할 창·글꼴 확대 최종 확인 |
| 4. DO 없음 | 차량에서는 정차 중심으로 제품 구성. 실제 차량 화면에 예시 위험·가짜 주행 경로를 표시하지 않음. 길 안내는 기본 Pleos 내비로 넘김 | 자체 EchoRoad 주행 앱을 제공하려면 별도 DO/CarUxRestrictions 설계와 검증 필요 |
| 5. 공용 서버 | 차량 UI의 내비·주변 찾기는 NaviHelper로 연결. 정차 시간·집중·할 일·오디오는 네트워크 없이 동작. 일반 Android/웹은 체험용 공개 서버와 설정 가능한 자체 서버를 유지 | 계약 서비스 자격 증명은 미제공. 공용 서버 SLA·전세계 가게 완전성은 보장 불가 |
| 6. README | 필수 4항목을 처음 표로 정리, 일반 개발 환경 명령, 라이선스·대표 문서 3개 제시 | 없음 |
| 7. 저장소 크기 | 옛 웹 ZIP 7개와 과거 검사 PNG를 현재 트리에서 제거. v0.10.5 태그 원본과 Git blob 일치를 검사 후 다운로드 링크 제공 | `uploads.github.com` 차단으로 Releases asset 이전은 미완료. 전체 Git 이력은 보존 |
| 8. 영상 | 대표 기존 영상 1개 지정. 브라우저·합성 GPS 사실 명시. APK의 실제 연속 화면 녹화 도구 유지 | 공식 Pleos 영상 미생성. 일반 Android KVM CI 실행·녹화 결과는 아래 기록으로 구분 |
| 9. 블라인드 | 앱 User-Agent에서 개인 저장소 링크 제거, 앱 이름·버전·팀 식별자 유지 | 저장소 소유 계정·커밋 이력 허용 범위는 운영진 확인 필요. 계정 생성/이력 재작성은 수행하지 않음 |
| 10. 문서 | README에서 검토 문서 3개만 연결. 과거 원본 JSON/GZIP은 `test-fixtures/validation`으로 이동, 옛 화면은 릴리스 태그 링크 | 원본 검증 자료는 재현성을 위해 보존 |
| 11. 테스트 | 이 작업 환경에는 JDK 21·Android SDK 35가 설치되어 있어 실제 Gradle 빌드·Robolectric 테스트 실행 | Robolectric과 실제 차량/에뮬레이터 검증은 별개 |

## 차량 신호 처리 기준

- 속도는 m/s→km/h, 후진은 절댓값, P 기어는 정확한 enum을 사용합니다. GPS로 주차를 추정하지 않습니다.
- 속도/기어의 신선도·역순·오류·권한 상실은 정차 허용을 취소합니다. 다시 수신해도 P·0의 1.5초 확인을 거칩니다.
- 배터리·충전은 선택 속성입니다. 읽기 실패가 정상적인 속도/기어를 취소하지 않습니다. 미지원/만료/NaN/범위 밖 값은 숨기며 `68%`, `40분` 같은 기본값을 넣지 않습니다.
- AAOS 배터리는 Wh/Wh 비율이며 0~100%로 검증합니다. Pleos EvBattery의 공식 값은 정수 %입니다. 충전 포트 연결만으로 충전 중이라고 판단하지 않습니다.
- API 초기화 성공과 실제 Get 수신을 구분합니다. NaviHelper 화면 전환 요청을 경로 탐색 성공으로 표시하지 않습니다.
- 기본 APK는 AAOS만 포함합니다. `-PpleosSdk=true` 프로필에만 공식 Vehicle 2.3.2 / NaviHelper 3.0.5 의존성이 들어갑니다.

## 실행 근거

기본 프로필: 공통 엔진 122개 + Android 99개 = 221개 테스트 통과. 녹화 증거 검사 4개, 선택적 검색 gateway 11개도 통과했습니다. Android lint 오류 0개, 기존 경고 4개입니다. 세로 360×800, 단일 창 600×800, 가로 1280×800은 합성 신호를 사용하는 실제 Compose 렌더링/조작 검사입니다. 이 검사를 Pleos Connect Emulator 실행으로 부르지 않습니다.

현재 작업 브랜치의 `apk/`는 **0.11.0-rc1 (versionCode 16), AAOS 기본 프로필, SDK 미포함**입니다. 기존 팀 인증서로 서명했고 서명·정렬 검사를 통과했습니다. 공식 SDK 포함 최종 제출본은 서버 접근 복구 후 다시 생성해야 합니다.

APK SHA-256: `ac125146b0be6efbca171e5aa080ba750c698c3eb289207e13b0ebe6ef82a02e`. [기계 판독 검사 결과](validation/submission-rc1.json).

## 공식 근거 및 남은 외부 조건

- [주행 규제 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy): DO 미선언 앱은 Parking에서 사용 가능, Drive에서 사용 불가. 현재 앱은 이 정차 구성을 선택했습니다.
- [NaviHelper 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions): 기본 내비 화면 요청은 NAVI_CUSTOM_MAP. 실제 검색·경로 품질과 제공 지역은 차량 기본 내비의 범위입니다.
- 공식 SDK·이미지 서버 `nexus-playground.pleos.ai`와 Release 업로드 서버 `uploads.github.com`의 환경 네트워크 접근 복구가 필요합니다. 차단을 우회하거나 가짜 SDK/영상으로 대체하지 않았습니다.
- 블라인드 규정의 저장소 소유 계정/기존 커밋 적용 범위는 공모전 운영진의 서면 확인이 필요합니다. 계정 이름을 바꾸는 것만으로 기존 이력까지 익명화되지 않습니다.

## 실행 영상의 구분

- 로컬의 일반 Android 35 에뮬레이터는 소프트웨어 모드에서 약 251초 만에 부팅했습니다. APK 시작 후 ADB/UI 응답 시간 초과가 발생해 매끄러운 실행·녹화를 완료했다고 판정하지 않았습니다. 앱 처리 속도와 소프트웨어 에뮬레이션 속도를 혼동하지 않습니다.
- GitHub Actions의 별도 `android-device` 작업은 KVM이 있는 일반 Android 35에 저장소의 **서명 APK 자체**를 설치하고 설치 APK의 SHA-256까지 대조합니다. `adb shell screenrecord`로 연속 녹화하며 스크린샷을 연결해 동영상으로 만들지 않습니다. 영상 속 차량 신호는 앱 내부 데모입니다.
- 공식 Pleos 이미지는 확보하지 못했습니다. 일반 Android 영상이 생성되어도 Pleos 서비스 검증/시연을 완료한 것으로 표시하지 않습니다.
