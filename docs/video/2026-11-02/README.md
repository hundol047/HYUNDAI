# CabinOS ONE 0.11.2 기능 시연

퓨처랩스 · 2026-11-02 · 실제 Android APK 화면과 Android 내부 출력 소리를 함께 녹화했습니다. 사진 슬라이드나 별도 더빙이 아닙니다. 각 영상은 독립된 실행의 연속 녹화입니다.

[APK·영상 다운로드](https://github.com/hundol047/HYUNDAI/releases/tag/v0.11.2-preview-20261102) · [동일 APK와 소스가 들어 있는 저장소](https://github.com/hundol047/HYUNDAI/tree/v0.11.2-preview-20261102) · [검토본 설치 안내](../../../preview/README.md)

**[전체 보기 영상 — 약 10분](https://github.com/hundol047/HYUNDAI/releases/download/v0.11.2-preview-20261102/CabinOS_ONE_all_features.mp4)**: 아래 5개 실제 실행 영상을 순서대로 연결한 편집본입니다. 각 원본의 화면과 출력 소리를 유지했으며 하나의 연속 실행으로 표시하지 않습니다.

## 영상별 기능

| 영상 | 직접 조작한 기능 |
|---|---|
| [01 주요 기능](https://github.com/hundol047/HYUNDAI/releases/download/v0.11.2-preview-20261102/CabinOS_ONE_overview.mp4) | 충전 대시보드, 집중 시작, 할 일 추가·완료·삭제, 배경음 선택, 충전 시간 변경, 주행·신호 대기 잠금, EchoRoad 지도, 휴식·호흡, 기록, 웜 샌드·화이트·블랙, 세로 화면 |
| [02 정차 계획](https://github.com/hundol047/HYUNDAI/releases/download/v0.11.2-preview-20261102/CabinOS_ONE_stop-plan.mp4) | 수동 출발 시간, 집중 시작, 소리 한 번에 끄기, 여유 시간 내 연장, 충전 시간 감소에 따른 세션 조정·연장 제한, D/0 잠금, 주차 복귀 |
| [03 내비게이션](https://github.com/hundol047/HYUNDAI/releases/download/v0.11.2-preview-20261102/CabinOS_ONE_navigation-review.mp4) | 서울 GPS 시뮬레이션, 좌표 목적지 검색·선택, 실제 OSRM 경로, 자동 지도 확대, 지도 이동·내 위치 복귀, 한국어 안내 다시 듣기, 안내 종료 |
| [04 장소·사진](https://github.com/hundol047/HYUNDAI/releases/download/v0.11.2-preview-20261102/CabinOS_ONE_place-photos.mp4) | 파리 GPS 시뮬레이션, 실제 주변 장소 목록, 카페 2곳·공원 사진, 목록 스크롤, 카페·산책·쇼핑 분류 전환 |
| [05 배경 소리 6종](https://github.com/hundol047/HYUNDAI/releases/download/v0.11.2-preview-20261102/CabinOS_ONE_audio-review.mp4) | 백색 소음·자연의 소리·잔잔한 톤·빗소리·계곡 소리·파도 소리의 실제 재생, 소리 끄기 |

전체 기능의 모든 조건을 검증한 영상은 아닙니다. 아래 미검증 항목을 함께 확인하세요.

## APK와 실행 환경

- APK: `preview/퓨처랩스_CabinOS_ONE_preview.apk`, `0.11.2-preview` / versionCode 21, 패키지 `io.cabinos.one.preview`.
- SHA-256: `f685dbce8ff531e921029c1fb961eafe04fee1fe2e70d8451d3fa184af049ba4`.
- 앱 소스 커밋: `bc83d16787a6363165c3d421c5b2533542e96f11`.
- 일반 Android 13 / API 33 x86_64 에뮬레이터, scrcpy 5.0.1의 실제 화면·내부 오디오 캡처. SDK 포함 빌드이지만 공식 Pleos 에뮬레이터는 아닙니다.
- 차량 입력은 앱 데모, 내비·장소 GPS는 `adb emu geo fix`입니다. 실제 운전이나 실내 GPS 수신 결과가 아닙니다.
- 한국어 음성은 에뮬레이터에 별도 설치한 [Sherpa ONNX TTS 엔진](https://github.com/k2-fsa/sherpa-onnx)의 Supertonic 3 모델입니다. 영상용 별도 더빙이 아니라 앱에서 해당 엔진으로 출력한 음성입니다. 이 엔진·음성팩은 CabinOS APK에 포함하지 않으며 사용자 기기의 엔진·언어팩에 따라 음질이 다릅니다.
- 최신 APK는 기존 휴대폰 검토본과 동일한 **Android 테스트 서명**입니다. 팀 정식 제출용 키 서명이 아닙니다. `apk/`의 0.11.0 제출본은 변경하지 않았습니다.

## 검증 범위

[소스 CI](https://github.com/hundol047/HYUNDAI/actions/runs/41427444496)에서 공식 SDK 포함 단위 테스트·debug/release 빌드·lint, 최신 검토 APK의 Android 13·15 설치·실행 검사를 통과했습니다. core/Android 단위 테스트는 545개 통과·실패 0개입니다. [녹화 CI](https://github.com/hundol047/HYUNDAI/actions/runs/41428891716)의 5개 시나리오가 모두 통과했고, UI 검사 **49개 통과·실패 0개**입니다. 내비 음성·배경음의 재생 신호와 배경음 중지 후 무음 검사도 통과했습니다. [검증 기록](verification.json)과 각 `*-evidence.json`, `*-audio.json`에서 검사 항목과 영상 SHA-256을 확인할 수 있습니다.

**이번 영상에서 검증하지 않은 기능:** 실제 마이크 STT·잡음 환경, 모든 언어 목소리, 실제 삼성 휴대폰 스피커·GPS, 공식 Pleos 차량 SDK 값 변화·차량 권한, 실제 도로 주행·교통 정확도, 카메라/방지턱 전체 누락 여부, 인증이 필요한 EchoRoad 공유·실시간 교통 서버, 실제 보행 왕복 경로와 복귀 알림, 장시간·재부팅 알림, 모든 장소 사진의 일치 여부. 명령 창의 예시 문장을 누르는 장면은 마이크 음성 인식 시연이 아닙니다.

일반 Android의 내비 화면을 차량에서 주행 중 사용할 수 있다는 뜻이 아닙니다. 차량용 정차 전용 정책과 유효한 속도 0 + P 1.5초 확인·신호 소실 시 잠금은 그대로입니다.

지도·사진 출처는 화면과 [출처 문서](../../THIRD_PARTY.md)에 표시합니다. 실제 사진이 없는 장소의 대표 이미지는 실제 장소 사진과 구분합니다.
