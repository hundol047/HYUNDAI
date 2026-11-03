# CabinOS ONE 0.11.1 배포 준비

소스는 versionName 0.11.1 / versionCode 20입니다. **현재 apk/는 0.11.0 그대로입니다.**
아래 내용은 다음 Release용 초안이며 아직 배포된 변경으로 안내하지 않습니다.

## 변경 사항

- 이전 정차의 출발 마감·도보 왕복 예약을 실제 주행 전환 시 초기화합니다.
- 자연스러운 충전 잔여 시간 감소가 출발 음성을 반복하지 않도록 수정했습니다.
- 차량 권한 요청 이력을 저장하고, 같은 결과로 불필요하게 재연결하지 않습니다.
- 권한 창 표시 중 화면이 재생성되어도 허용 결과를 반영하며, 영구 거부 시 권한 버튼이 앱 설정을 엽니다.
- 서머타임으로 누락·반복되는 도착 시각은 다음 유효한 미래 시각으로 선택합니다.
- Pleos 읽기 불가 capability는 30초 후 재확인합니다.
- 실제 SDK 좌석 점유·문·창문·공조·워셔액을 정차 중 제안에 활용합니다. 새 항목은 기기 검증 전입니다.
- 도착 시·분 선택, 집중 할 일 프리셋, 음성 서비스 미지원 시 명령 버튼을 제공합니다.
- 웹의 첫 사용 흐름을 정차·충전 중심으로 정리하고 주행·OSM 내비는 접힌 부가 데모로 이동했습니다.
- 속도 신호 분석 도구와 전체 시연 기록 도구를 추가했습니다. 대안 신선도 판정은 **꺼져 있습니다**.

- 차량 연결 종료 예외에도 먼저 신호를 무효화하고, 초기화 실패 시 부분 연결을 해제합니다.
- 중지·재연결·권한 상실 후의 늦은 GPS 콜백을 거부합니다.
- 저장된 계획 읽기 실패 시 기존 데이터를 덮어쓰지 않으며, 할 일 읽기의 일시 오류는 재시도합니다.

[배포 전 추가 점검 보고](PREDEPLOY_RUNTIME_AUDIT_KO.md)

## 계정 등록 없이 준비할 수 있는 범위

팀명은 **퓨처랩스**입니다. Pleos App Market Console 개인 등록은 현재 보류합니다. 마켓 등록과 SDK 포함 빌드·직접 APK 설치·서명 준비는 별개입니다. 이 환경에서는 공식 SDK 빌드가 가능하지만 KVM이 없어 공식 에뮬레이터 전체 시연을 완료하지 못했습니다.

## 검증된 후보 만들기

기존 팀 키 폴더와 별도로 확인한 인증서 SHA-256을 준비합니다. 키 폴더에는 기존 `release.p12`와 `password` 파일이 필요하며 저장소 밖에 두어야 합니다. 비밀번호를 명령줄이나 채팅에 쓰지 마세요. 새 키를 임의로 만들거나 후보 APK에서 얻은 지문을 신뢰값으로 사용하지 않습니다.

`CABINOS_SIGNING_DIR`, `CABINOS_TEAM_CERT_SHA256`를 설정한 뒤, 커밋된 깨끗한 소스에서 실행합니다.

```bash
bash scripts/package-submission.sh
```

이 명령은 SDK 포함 테스트·release 빌드·lint 후 별도 임시 폴더에서 서명·정렬·인증서·체크섬 검사를 수행합니다. 모두 통과한 경우에만 `artifacts/submission-candidate/`를 만듭니다. 기존 폴더는 덮어쓰지 않으며 다른 폴더가 필요하면 `CABINOS_CANDIDATE_DIR`을 지정합니다.

후보 폴더에는 다음 파일이 있습니다.

- `퓨처랩스_CabinOS_ONE_release.apk`: 검증된 팀 서명 후보
- `SHA256SUMS`: 후보 APK 체크섬
- `verification.json`: 바이너리 검사 결과
- `candidate.json`: 팀명·소스 커밋·해시 및 기기 검증/게시 미완료 표시

**스크립트는 저장소의 apk/ 파일을 교체하거나 GitHub·마켓에 게시하지 않습니다.** 실패한 후보는 제거합니다. 실제 서명은 기존 팀 키가 준비된 뒤 수행하며, 자동화 검증의 가짜 테스트 키·산출물을 실제 APK 증거로 사용하지 않습니다.

## 같은 후보로 실제 검증하기

```bash
adb install -r -g "artifacts/submission-candidate/퓨처랩스_CabinOS_ONE_release.apk"
python3 scripts/verify-release-candidate.py \
  --apk "artifacts/submission-candidate/퓨처랩스_CabinOS_ONE_release.apk" \
  --checksums artifacts/submission-candidate/SHA256SUMS \
  --certificate-sha256 "$CABINOS_TEAM_CERT_SHA256"
scripts/demo/run-full-scenario.sh "artifacts/submission-candidate/퓨처랩스_CabinOS_ONE_release.apk" artifacts/full-scenario
```

공식 Pleos 환경에서 P/0·D/0·D/주행·SOC/충전 변화·출발 계획·세션 종료를 확인합니다. [전체 시연 절차](../scripts/demo/pleos-full-scenario.md)에 따라 동일 APK 해시를 대조하고 60~120초 연속 영상을 남깁니다. **새 영상 링크는 실제 검증 후 입력합니다.** 바이너리 검사 통과는 차량 동작이나 배포 승인을 의미하지 않습니다.

## 제출·게시 전 남은 작업

1. 승인된 PR을 main에 반영합니다. 현재 0.11.1 변경은 PR #2 브랜치에 있습니다.
2. 전체 실제 시연을 통과한 동일 후보를 `apk/퓨처랩스_CabinOS_ONE_release.apk`에 반영하고 저장소 기준 체크섬을 생성합니다.
3. README·제출 검토표의 버전·해시·대표 영상 링크를 동일 파일 기준으로 갱신합니다.
4. 승인된 배포 범위에서 GitHub Release/공모전 제출을 진행합니다. Pleos App Market 등록은 개인 계정 지원 여부가 해결된 이후 별도로 진행합니다.

현재 저장소의 0.11.0 APK는 최신 소스와 버전이 달라 배포 검사에 실패하는 것이 정상입니다. 이 작업에서는 APK 교체·팀 키 서명·Release 게시를 수행하지 않았습니다.

표시등·TPMS는 capability 확인의 비공개 권한 요구로 제외했습니다. Tire의 Float API를 저압 Boolean으로 해석하지 않습니다.
