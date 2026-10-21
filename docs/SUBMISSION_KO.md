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
cd /workspace/HYUNDAI
source scripts/env.sh
./scripts/package-submission.sh
```

테스트·Lint·Release 빌드 후 제출 폴더에 APK와 SHA-256 체크섬을 생성합니다. 최초 실행 시 `/workspace/.signing/cabinos-one`에 전용 서명키와 비밀번호 파일을 생성하며, 이후에는 같은 키를 재사용합니다. 다른 환경에서는 `CABINOS_SIGNING_DIR`로 **저장소 밖**의 안전한 경로를 지정하세요. 앱 업데이트를 위해 키와 비밀번호 파일을 함께 안전하게 백업해야 합니다. 이 파일들은 GitHub에 올리면 안 됩니다. 소스만 복제한 새 머신에서 키를 새로 만들면 기존 APK와 서명이 달라지므로 기존 앱 위에 업데이트할 수 없습니다.

## 제출 전 남은 확인

- APK를 실제 심사 환경/기기에 설치하고 실행할 수 있는지 확인.
- Pleos SDK·실차 연동 범위와 공모전 지원 환경 확인. 현재 APK는 명시적인 시뮬레이션 기반이며 해당 연동은 미완료.
- 음성팩/인식 서비스가 없을 때 명령 버튼으로 조작 가능. 실제 한국어 TTS/STT는 해당 서비스가 있는 기기에서 확인 필요.
- 시연 영상과 사업계획서 등 APK 외 제출 자료는 공식 안내에 맞춰 준비.
- Private 저장소라면 심사용 계정 초대와 권한 확인은 계정 소유자가 진행.

파일 형식 충족과 실차 SDK 기능 충족은 별개입니다. 현재 지원 범위는 SDK_CAPABILITIES.md, LIMITATIONS.md 및 VALIDATION.md를 함께 확인하세요.
