# 출시 전 2~5번 점검 — 퓨처랩스

## 2. 장소 검색

한 분류에 결과가 있어도 비거나 실패한 다른 분류를 보조 검색합니다. 보조 요청은 누락된 분류만 대상으로 하고 반경을 주 검색과 같은 2km로 맞췄습니다. 정원·자연보호구역도 포함합니다. 일부 성공 결과는 유지하고, 전부 실패하면 이전 목록을 지우는 빈 성공으로 처리하지 않습니다.

[실제 Photon 조회](nearby-http.json): 미래캠퍼스·서울·파리 4분류씩 12회. 미래캠퍼스 카페·식당은 0건입니다. [실제 보조 검색](nearby-fallback-http.json)은 첫 서버 502, 두 번째 서버 시간 초과였습니다. 이 지역의 카페·식당 누락은 해결 완료가 아닙니다. 등록 데이터가 충분하고 가용성이 확보된 장소 공급원 또는 자체 검색 인프라가 추가로 필요합니다.

## 3. 내비게이션

[실제 외부 응답](navigation-http.json): 7개 도시의 목적지·경로 14건과 지도 타일 1건 성공. 합성 응답이나 실제 주행 결과가 아닙니다. 실시간 교통 최적 경로, 차선 안내, 모든 도로·카메라·방지턱의 정확도를 보장하지 않습니다.

네트워크·위치 사용 중단, GPS 무효화, 안내 종료 시 이전 교통 색상을 지웁니다. 늦게 도착한 비동기 응답도 중단한 정보를 복원하지 못하도록 검사합니다. 교통 공급원 미설정 상태에 빨강·노랑·초록 데이터를 만들어 넣지 않습니다. 현재 한국 교통 서버·교통 반영 경로 연결은 남아 있습니다.

## 4. 실기기

이 작업 환경의 `adb devices`에는 연결 기기가 없고 `/dev/kvm`도 없습니다. GitHub CI의 Android 에뮬레이터를 사용합니다. S20+ 실물·최신 Pleos 현장 실행은 완료로 표기하지 않습니다. 기존 테스트를 삭제하거나 안전 잠금을 완화하지 않습니다.

### S20+에서 확인할 절차

PC에 Android platform-tools를 설치하고 휴대폰의 USB 디버깅을 허용합니다. 아래 SERIAL은 `adb devices`에 나온 해당 휴대폰 식별자로 로컬에서 대체합니다. 식별자·위치가 포함된 원본 로그는 공개 저장소에 올리지 않습니다.

```bash
adb devices
adb -s SERIAL install -r -g "preview/퓨처랩스_CabinOS_ONE_preview.apk"
adb -s SERIAL shell am start -n io.cabinos.one.preview/io.cabinos.one.MainActivity
```

정차한 상태에서 다음을 직접 확인합니다. 실패 시 앱 버전, 화면의 오류 문구, 실패 단계만 기록합니다.

1. 정확한 위치 허용 후 내 위치 사용. 실제 위치와 지도점·정확도 확인. 위치 권한 해제 시 안내 중지, 복구 후 새 위치 확인.
2. 카페·식당·산책·쇼핑 전환, 목록 끝까지 스크롤, 없는 결과와 검색 실패 구분.
3. 목적지 선택 → 앱 안에서 경로·전체 화면 안내. 음성 목적지 검색 → 결과 선택. 잘못된 동명 장소 자동 선택 여부 확인.
4. 집중 세션 소리 선택 → 세로/가로 전환 → 소리·타이머 유지. 전화·오디오 포커스 상실 시 중단 확인. 언어별 TTS 설치 상태 확인.
5. 앱 이탈·복귀·네트워크 끊김·GPS 끊김·30분 세션 중 경고 반복과 오류 확인. 이 절차는 실제 운전 중 화면 조작을 요구하지 않습니다.

### Pleos Connect 2.1.0 / API 34

팀 키 서명 후 동일 APK 해시를 대조하고 `scripts/record-vehicle-emulator.py --help`에 따라 녹화합니다. P/0 1.5초 해제, D/0 잠금, D/주행 잠금, UNKNOWN·권한 상실 즉시 잠금, SOC·충전 시간에 따른 세션 마감, NaviHelper 실제 ETA를 확인해야 합니다. SDK 없는 모형이나 일반 Android 녹화를 Pleos 증거로 사용하지 않습니다.

## 5. 제출 APK

최신 소스의 공식 SDK 포함 최적화 release를 빌드합니다. 서명 전 후보는 설치·제출용이 아닙니다. 기존 `apk/`는 0.11.0이고 최신 수정이 없으므로 그대로 최종 제출하면 안 됩니다.

`bash scripts/package-submission.sh`를 실행해 `CABINOS_SIGNING_DIR` 미설정으로 중단됨을 확인했습니다. 기존 팀 키가 없다고 전달받았으며 임의의 새 키나 debug 키로 제출 파일을 바꾸지 않습니다.

기존 키를 복구한 PC에서 다음을 실행합니다. 비밀번호·키는 채팅이나 저장소에 넣지 않습니다.

```bash
export CABINOS_SIGNING_DIR=/private/path/to/existing-team-key
export CABINOS_TEAM_CERT_SHA256=기존_팀_인증서의_별도로_확인한_SHA256
bash scripts/package-submission.sh
```

스크립트는 공식 SDK 테스트·release·lint, 서명·정렬·인증서·버전·해시를 확인하고 별도 후보 폴더를 만듭니다. 후보와 동일한 APK를 Pleos에서 검증한 후에만 `apk/퓨처랩스_CabinOS_ONE_release.apk`와 SHA256SUMS를 갱신합니다. 키를 복구할 수 없다면 새 서명 전환과 기존 설치본 업데이트 불가 문제를 먼저 결정해야 합니다.

## 실행한 검사

- 공식 SDK 포함: core 172개 + Android 232개 = 404개, 실패·건너뜀 0.
- 서버 25개 + 도구 55개 = 80개, 실패 0. 합계 484개.
- `:app:assemblePhonePreview :app:assembleRelease` 성공. `:app:lintDebug :core:jsBrowserDistribution` 성공.
- [서명 전 차량용 후보 식별 정보](build-candidate.json): 실제 빌드·SDK 표시·버전·비디버그·정렬 확인. 서명·제출 가능 판정은 false.
- [현재 APK 연속 실행 영상과 해시 대조](../video/release-review/README.md): 실제 Android 에뮬레이터, 차량·GPS 모의 입력. S20+·최신 Pleos 실기기 검증은 남아 있습니다.
