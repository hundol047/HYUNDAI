# CabinOS ONE 휴대폰 검토본 · 퓨처랩스

Android 10 이상 휴대폰에서 기능을 검토하는 테스트 앱입니다. 삼성 실물 기기 검증을 완료했다는 뜻은 아닙니다. 차량용 가로 화면 규격은 유지하며, 작은 창에서는 기존 반응형 화면을 사용합니다.

- [검토용 APK](퓨처랩스_CabinOS_ONE_preview.apk) · [SHA-256](SHA256SUMS) · [소스와 빌드 정보](build-info.json)
- 앱 이름: **CabinOS ONE 테스트**. 기존 차량용 앱과 별도 설치되어 기존 앱과 데이터를 덮어쓰지 않습니다.
- 일반 휴대폰의 속도·기어·충전 값은 **데모 데이터**입니다. 실제 차량 신호는 AAOS/Pleos 차량 기기가 필요합니다.
- 지도·위치·음성 기능은 인터넷, 기기 권한, 설치된 음성 서비스에 따라 달라집니다.
- Android 테스트 키로 서명한 검토본입니다. 정식 배포나 공모전 제출용 `apk/` 파일을 대체하지 않습니다.

## 다운로드 QR

![휴대폰 검토용 APK 다운로드 QR](install-qr.png)

[같은 APK 직접 다운로드](https://raw.githubusercontent.com/hundol047/HYUNDAI/89b44473335806c053f4ffb66fc6f5a6e0a4d270/preview/%ED%93%A8%EC%B2%98%EB%9E%A9%EC%8A%A4_CabinOS_ONE_preview.apk) · QR은 이 APK 커밋에 고정되어 있습니다. 새 파일로 바뀌지 않습니다.

## 설치

APK를 내려받아 열고, 설치 앱의 ‘출처를 알 수 없는 앱 설치’ 허용을 요청받으면 해당 설치 과정에서만 허용하세요. 설치 후 불필요한 설치 권한은 다시 해제할 수 있습니다. QR과 같은 화면에서 내려받으려면 APK 링크를 직접 눌러도 됩니다.

## 확인할 동작

1. 데모 충전 대시보드에서 집중·휴식 세션을 시작하고 종료합니다.
2. 명령 버튼으로 25분 집중을 시작하고 할 일을 추가합니다.
3. 데모 주행 및 신호 대기로 전환하면 정차 전용 조작이 잠기는지 확인합니다.
4. 세로·가로 회전, 색상 설정, 지도와 권한 거부 후 안내를 확인합니다.

재현 빌드: `./gradlew -PpleosSdk=true :app:assemblePhonePreview`. Android SDK와 공식 Pleos Maven 접근이 필요합니다. 이 빌드는 기존 Android debug 키를 사용하며 팀 배포 키를 변경하지 않습니다.

설치 실행 검사는 GitHub Actions의 `phone-preview` 작업에서 APK 파일과 설치된 파일의 SHA-256을 대조한 뒤 수행합니다. 실행 결과와 영상은 해당 커밋의 Actions 기록을 기준으로 확인하세요. Android 에뮬레이터 검사는 실제 삼성·Pleos 기기 검사를 대체하지 않습니다.
