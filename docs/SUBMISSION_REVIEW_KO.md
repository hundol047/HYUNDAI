# 제출 검토표 — 0.11.0

**공식 SDK 포함 APK의 빌드·서명·실제 Pleos SDK 응답과 공개 진단 영상까지 확보했습니다. 전체 차량 상태 변경 시연은 미완료이므로 제출 준비 완료로 표시하지 않습니다.** 0.11.0 소스와 APK는 PR #1로 main에 병합되었습니다.

## 파일과 실행 증거

- APK: [`../apk/퓨처랩스_CabinOS_ONE_release.apk`](../apk/퓨처랩스_CabinOS_ONE_release.apk), versionName **0.11.0**, versionCode **19**.
- APK SHA-256: `7279b860a012c08d5f929573b3c1bdfd245d1ac8b8f6af74e5e6b4d4451d0543`.
- 팀 인증서 SHA-256: `33ebb1d6c0e297bb734bb85f55d05edf6faf66375a9448ae2e73a3457e9bb9e0`. 키·비밀번호는 저장소 밖에 유지합니다.
- [공식 Pleos 실제 실행 진단 영상](https://github.com/hundol047/HYUNDAI/releases/tag/v0.11.0-pleos-preview-7279b860): 95.99초, 2560×1440, ADB 연속 녹화 원본. 설치 APK를 pull해 위 해시와 대조했습니다. 전체 영상 디코딩 오류 0, 실제 화면 검토 완료. 사진 연결·합성 영상이 아닙니다.
- [기계 판독 검증 기록](validation/submission-0.11.0.json), [핵심 기능 ↔ SDK API](SDK_CAPABILITIES.md), [로컬 PC 실행·녹화 명령](PLEOS_VEHICLE_STATE_KO.md).

## 완료된 구현·검사

| 항목 | 결과 |
|---|---|
| SDK | 공식 Vehicle 2.3.2 / NaviHelper 3.0.5 AAR로 debug/release 컴파일·빌드 |
| 핵심 입력 | SDK SOC 목표 → 활동 마감, 충전 스위치 → 충전 예산 유효성, NaviHelper 목적지 시간 → 출발 마감. 장치 전체 시나리오 통과와 구분 |
| 안전 | 유효한 0 + P 1.5초 후 정차 기능, D/0·만료·신호 소실 잠금. 차량에서 데모 조작판 숨김. DO 미선언 |
| 테스트 | 공통 131/131 + Android 101/101, 녹화 판정 5/5, 장소 게이트웨이 11/11 |
| lint | 오류 0 / 기존 경고 4 |
| 패키지 | SDK 포함, 최적화·비디버그 release, 기존 팀 키 서명·zipalign·체크섬 통과 |
| 문서 | README 필수 4항목·라이선스·main clone, API 매핑, 재현 명령 |

```bash
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
python3 -m unittest discover -s scripts/tests -v
python3 -m unittest discover -s server -v
sha256sum -c apk/SHA256SUMS
```

위 Gradle 명령은 현재 APK 소스에서 3분 30초에 성공했습니다. JVM의 합성 입력은 실제 차량 응답의 증거가 아닙니다. Robolectric에서는 실제 AAR를 로드하되 차량 서비스 bind 실패를 사용하고 클래스별 JVM을 격리합니다.

코드 커밋 `dd15b3a`의 [push CI](https://github.com/hundol047/HYUNDAI/actions/runs/40094004076)와 [PR CI](https://github.com/hundol047/HYUNDAI/actions/runs/40094007815)가 통과했습니다. 이후 문서 변경의 최신 결과는 PR checks에서 확인하세요.

## 실제 공식 Pleos에서 확인한 범위

[실행 40094004618](https://github.com/hundol047/HYUNDAI/actions/runs/40094004618), check `120872359016`. 공식 Connect 2.1.0 / API 34 이미지의 배포 SHA-1 `b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa`와 fingerprint를 대조했습니다.

- Android 부팅, 동일 서명 APK 설치·실행, 요청 차량 권한 부여를 확인했습니다.
- 실제 EvBattery SOC **100%**, 충전 스위치 **false**, Brake **false**, `sdkSocUsed=true`를 확인했습니다. Brake는 진단이며 정차 판정의 대체 입력이 아닙니다.
- AAOS P/0의 1.5초 확인 후 잠금 해제와 UNKNOWN 시 잠금을 관측했습니다. 지속적으로 안정된 정차 신호는 확인하지 못했습니다.
- 이전 실행에서 NaviHelper `onDestinationInfo` 콜백을 관측했습니다. 실제 목적지 ETA 값·도착 계획 동작의 검증은 남아 있습니다.
- 공개 영상은 실제 SDK 연결을 보여주는 **진단 영상**입니다. 시간 설정 조작이 완료되지 않아 집중 시작·SOC 목표 자동 종료는 시연하지 못했습니다. 일반 Android/브라우저 데모로 이 조건을 대신하지 않습니다.

## 실행·영상에서 발견해 수정한 부분

- 실제 release 로그의 Gson `TypeToken must be created with a type argument` 오류: 공식 Gson R8 지침에 따라 제네릭 정보와 Vehicle 모델 필드를 보존했습니다. 수정 후 실제 SDK 응답을 받았으며 해당 오류가 재현되지 않았습니다.
- 슬라이더만으로 시간 선택이 어려웠던 점: 최소 높이 48dp의 15/30/45/60분 버튼을 추가했습니다.
- 느린 부가 배터리 Binder 조회가 필수 속도·기어 작업을 막을 수 있는 구조: 작업 스레드를 분리했습니다. 다만 실제 영상의 신호 만료는 계속 관측돼 이 수정만으로 해결됐다고 주장하지 않습니다. TTL·안전 확인 시간은 완화하지 않았습니다.

## 남은 완료 조건과 막힌 원인

1. 공식 Virtual Console의 기어·속도·SOC·충전 스위치 요청이 **HTTP 503 / `emulator_inactive`**입니다. 공개 UI의 **AVD Enabled** 요청도 실패합니다. P→D/0→D/주행 및 SOC·충전 상태 변화 시나리오는 미통과입니다.
2. 공식 이미지의 profile·vehicle_ext·diagnostic 서비스 종료와 입력기 오류도 관측했습니다. 앱 신호 만료의 단일 원인으로 확정하지 않습니다. 동일 이미지/SDK/APK를 로컬 하드웨어 가속 PC에서 비교해야 합니다.
3. 안정된 실제 신호로 시간 설정·집중 시작·SOC 목표 종료, 실제 내비 목적지 ETA 반영, Pleos 분할 창을 추가 검증해야 합니다. 그 뒤 **같은 APK**로 60~120초 전체 시연을 다시 녹화해야 합니다.
4. CI 진단 job 성공은 수집 완료이며 전체 시나리오 통과가 아닙니다. `submissionReady=false`를 유지합니다.
5. GitHub 소유 계정이 URL에 표시되는 블라인드 문제는 주최 측 확인이 필요합니다. 계정 이전·기존 이력 재작성은 하지 않았습니다.

현재 클라우드에는 `/dev/kvm`이 없어 별도 KVM runner를 사용했습니다. [로컬 PC 가이드](PLEOS_VEHICLE_STATE_KO.md)에 공식 이미지·Console·설치·동일 APK 녹화 명령과 503 재현 절차가 있습니다. 신호 검증 완화·가짜 SDK 값·합성 영상으로 남은 항목을 통과 처리하지 않습니다.
