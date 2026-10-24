# 퓨처랩스 제출 안내

- 팀명: **퓨처랩스**
- 앱 이름: **CabinOS ONE**
- 한 줄 소개: 차량의 주행·정차 상황에 맞춰 위험 안내, 대기 시간 활용, 집중·휴식을 연결하는 통합 차량용 앱.
- 개발 저장소: https://github.com/hundol047/HYUNDAI
- 제출 APK 경로: `apk/퓨처랩스_CabinOS_ONE_release.apk`

APK는 `release` 빌드 변형이며 `debuggable=false`, R8 코드 최적화·리소스 축소를 적용합니다. 전용 팀 릴리스 인증서로 서명하고 APK 서명 검증·ZIP 정렬 검사를 수행합니다. Android 기본 debug 인증서로 서명한 패키지는 제출 폴더에 사용하지 않습니다.

## 제출 페이지 입력

저장소 주소와 위 APK 경로를 각각 입력하세요. 소스 코드와 APK가 같은 브랜치에 있어야 합니다. 저장소가 Private이면 공지된 **정확한 심사용 GitHub 계정**을 Collaborator로 초대하고 마감 전에 접근 여부를 확인해야 합니다. 이 작업에서는 심사용 계정 정보를 전달받지 않았으므로 초대나 권한 변경은 수행하지 않습니다. 저장소 공개 범위도 임의로 변경하지 않습니다.

APK가 GitHub 일반 파일 제한보다 작으면 `apk/`에 직접 포함합니다. 제한을 초과할 때만 GitHub Releases에 올리고 해당 Release 페이지 주소를 제출하면 됩니다.

## 재빌드·재서명

```bash
cd HYUNDAI
# JDK 21 / ANDROID_HOME 설정 후, 기존 팀 키를 저장소 밖의 경로에 복원
export CABINOS_SIGNING_DIR=/your/private/signing-directory
./scripts/package-submission.sh
```

테스트·Lint·Release 빌드 후 제출 폴더에 APK와 SHA-256 체크섬을 생성합니다. 최초 실행 시 `/workspace/.signing/cabinos-one`에 전용 서명키와 비밀번호 파일을 생성하며, 이후에는 같은 키를 재사용합니다. 다른 환경에서는 `CABINOS_SIGNING_DIR`로 **저장소 밖**의 안전한 경로를 지정하세요. 앱 업데이트를 위해 키와 비밀번호 파일을 함께 안전하게 백업해야 합니다. 이 파일들은 GitHub에 올리면 안 됩니다. 소스만 복제한 새 머신에서 키를 새로 만들면 기존 APK와 서명이 달라지므로 기존 앱 위에 업데이트할 수 없습니다.

## 제출 전 남은 확인

- APK를 실제 심사 환경/기기에 설치하고 실행할 수 있는지 확인.
- Pleos SDK·실차 연동 범위와 공모전 지원 환경 확인. 작업 브랜치의 후보 APK에는 AAOS 읽기 어댑터가 있으며 Pleos SDK 포함 최종본은 미완료. [최신 검사](SUBMISSION_REVIEW_KO.md).
- 음성팩/인식 서비스가 없을 때 명령 버튼으로 조작 가능. 실제 한국어 TTS/STT는 해당 서비스가 있는 기기에서 확인 필요.
- [최신 시연 영상과 녹화 근거](SEARCH_RECOVERY_KO.md)는 실제 브라우저 실행을 연속 녹화했습니다. 위치는 합성 GPS이며 실차·Android 실기기 녹화가 아닙니다. 실제 APK/차량 실행 영상이 추가로 필요한지는 공식 안내에서 확인. 사업계획서 등 별도 자료도 확인.
- Private 저장소라면 심사용 계정 초대와 권한 확인은 계정 소유자가 진행.

파일 형식 충족과 실차 SDK 기능 충족은 별개입니다. 현재 지원 범위는 SDK_CAPABILITIES.md, LIMITATIONS.md 및 VALIDATION.md를 함께 확인하세요.

## 공식 접수 안내 확인 (2026-10-21)

[공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)를 직접 확인했습니다. 접수 기간은 **2026-10-21~2026-11-15**, 접수 방식은 **이메일**입니다. 필수 서류는 [접수양식·개인정보 수집·이용 동의서 DOCX](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx)이며, 대표자와 팀원 전원의 동의·서명·날짜가 필요합니다.

- 접수 이메일: `appdevchallenge@hyundai-partners.com`
- 제목: `[차량용 앱 개발 공모전] 퓨처랩스_대표자명`
- 팀 참가 시 대표자가 팀원 전원을 CC하여 제출합니다.
- 시연 영상·저장소·APK는 1차 접수에서 선택 제출·가산점 항목입니다. 영상은 목업·프로토타입·화면 녹화를 인정합니다. 현재 영상은 브라우저 프로토타입 실행 녹화로 표기합니다.
- 2차 PT 대상 팀은 PT 전까지 실제 동작 가능한 앱 개발을 완료해야 합니다.

이 작업은 소스·APK·영상을 준비한 것이며, 개인 동의서 작성·서명이나 접수 이메일 발송은 수행하지 않았습니다.
