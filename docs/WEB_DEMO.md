# 웹 체험·Android 앱·실행 영상

같은 페이지에서 직접 조작하는 브라우저 데모, 실제 Android Compose UI 갤러리, 제출용 APK 다운로드, 실행 MP4를 제공합니다. 브라우저와 Android는 `core`의 Kotlin 상태 엔진을 공유합니다. 실차 연결은 미지원 상태로 명확하게 표시합니다.

## Android·웹 0.10.5 배포 파일

[GitHub Release](https://github.com/hundol047/HYUNDAI/releases/tag/v0.10.5)에서 전용 팀 인증서로 서명한 APK와 [`web-release/CabinOS_ONE_web_v0.10.5.zip`](../web-release/CabinOS_ONE_web_v0.10.5.zip)의 버전별 저장소 다운로드 링크를 제공합니다. 클라우드 프록시가 `uploads.github.com` 연결을 HTTP 403으로 차단해 Release 첨부파일 업로드는 완료되지 않았으며, 허용된 기존 Git 저장소에 배포 파일을 보관합니다. 웹 ZIP에는 인터랙티브 데모·Android UI 갤러리·APK·기존 MP4·새 모의 주행 MP4가 포함됩니다. HTTPS 정적 호스팅에 압축을 풀어 배포해야 하며 ZIP 다운로드 자체가 사이트 공개를 뜻하지 않습니다. GitHub Release에는 자동 24시간 만료를 설정하지 않았습니다.

음성 길찾기와 언어 선택은 [사용법과 실제 지원 범위](VOICE_NAVIGATION_KO.md)를 확인하세요. 기존 MP4는 0.5.0 녹화로 새 음성 기능의 녹화가 아닙니다.

## 공개 접속 상태

**공개 배포 완료:** https://hundol047.github.io/HYUNDAI/

2026-10-24 16:18 KST에 실제 HTTPS 사이트를 Chromium으로 확인했습니다. 같은 페이지에서 **직접 체험하기**, **Android 앱 화면 안내**, **서명 APK 다운로드**, **실행 영상**을 사용할 수 있습니다. Android 앱 화면 안내는 Compose 갤러리이며, APK를 브라우저 안에서 실행하는 기능은 아닙니다.

소유자의 Pages 활성화 후 [기존 검증된 배포 작업의 두 번째 실행](https://github.com/hundol047/HYUNDAI/actions/runs/40062127227)이 성공했습니다. 실제 배포한 앱 소스는 `e94616ab2a2b9f227f5c30f35bda9ad97a249c36`입니다. 데모 주행/주차 조작, Android 내비 이미지, 실제 지도 타일, 390px 모바일 배치, APK 바이트 일치, 78.28초 영상 재생·65초 탐색을 확인했습니다. [공개 사이트 검증 기록](validation/pages-v0105.json).

이전 Pages 미활성화·연동 앱 권한 부족으로 인한 배포 실패는 해결됐습니다. 이후 앱 변경은 main 푸시 또는 [배포 워크플로](https://github.com/hundol047/HYUNDAI/actions/workflows/pages.yml) 실행으로 배포할 수 있습니다. 이번 문서 갱신은 앱 코드를 바꾸지 않으므로 검증된 사이트를 다시 빌드하지 않습니다.

GitHub Pages는 이 클라우드 작업 환경의 종료와 관계없이 유지됩니다. **24시간 자동 만료를 설정하지 않았습니다.** 실제 가용성은 GitHub 서비스와 저장소 설정에 따릅니다.

## 로컬 재현

```bash
source scripts/env.sh
./scripts/build-web.sh
python3 scripts/serve-web.py --directory build/site
# 다른 터미널에서
npm ci --prefix scripts/web
node scripts/web/verify.cjs
node scripts/web/record-demo.cjs
```

브라우저 검사는 Playwright와 Chromium이 필요하며 영상 촬영은 FFmpeg도 필요합니다. 시스템 Chromium이 없으면 `scripts/web`에서 `npx playwright install chromium`을 실행하세요. `CABIN_CHROME`으로 별도 Chromium 실행 파일을 지정할 수 있습니다. 공개 미리보기는 로컬 임시 서버 대신 GitHub Pages를 사용합니다.

다운로드는 `apk/퓨처랩스_CabinOS_ONE_release.apk`의 바이트를 그대로 복사합니다. 검증 스크립트는 내려받은 APK의 SHA-256이 저장소 파일과 같은지 확인합니다. 새 APK를 패키징한 다음에는 웹 사이트도 다시 빌드해야 합니다.

## 글로벌 내비게이션 (0.4.0)

왼쪽 `내비`에서 국가 제한 없는 Nominatim 주소 검색·위경도 목적지, OSRM 자동차 경로, 실제 브라우저 GPS 기반 주행 안내를 사용할 수 있도록 구현했습니다. 공유 Kotlin NavigationTracker가 회전/진행/도착/재탐색을 계산합니다. HTTPS 위치 권한과 네트워크가 필요합니다. 차량/위험 예시와 실제 위치 경로를 구분하며, 0.4.0 당시에는 제공자 접속이 차단돼 미검증이었습니다. 당시 영상의 내비게이션 장면은 합성 서버 응답/GPS를 표시했습니다. 이후 버전의 실제 제공자 검사와 영상은 아래 기록을 확인하세요. [사용법과 제한](NAVIGATION_KO.md)을 확인하세요.

## 0.5.0 과거 검증

실내 테마·강조색·야간 지도는 설정에서 바꾸며 시각 설정만 localStorage에 보관합니다. 실제 OSM/Nominatim/OSRM HTTP 200과 서울 브라우저 지도/길 안내를 확인했습니다. GPS 입력은 합성 공개 좌표이며 실기기 검증은 별도입니다. `verify-appearance.cjs`와 `verify-navigation-live.cjs`, `scripts/check-navigation-live.py --all-regions`로 재현합니다. 당시 CI는 합성 fixture로 재현 가능한 47개 브라우저 검사를 실행했으며 실제 외부 서비스 가용성을 보장하지 않습니다.

0.10.2의 브라우저 회귀 범위는 118개이며 자세한 집계는 [검증 결과](VALIDATION.md)를 참고하세요. 배포 ZIP은 `./scripts/build-web.sh` 후 `python3 scripts/package-web-release.py`로 재생성합니다.

0.10.1부터 Android 내비 갤러리에는 실제 도로 타일이 표시된 Compose 실행 화면을 제공합니다. [실행 근거](ROAD_MAP_FIX_KO.md). 물리 기기 캡처는 아닙니다.

웹 r1은 이동 애니메이션 도중 화면 크기가 바뀔 때의 지도/핀 위치를 보완한 배포입니다. Android APK는 0.10.1 서명 파일을 사용합니다.

웹 r2는 복잡한 실제 경로의 모의 주행에서 발견한 회전 화살표 글꼴 깨짐을 SVG로 수정했습니다. 3개 도시의 실서버 지도·검색·경로에 합성 GPS를 입력해 끝까지 주행한 새 MP4와 [검사 기록](CHALLENGING_DRIVE_KO.md)을 제공합니다. Android APK는 동일한 0.10.1 서명 파일입니다.

0.10.2는 [서울시청→연세대 미래캠퍼스 113km 모의 주행](SEOUL_YONSEI_DRIVE_KO.md)에서 확인한 상호 공백 검색 누락·진출 방향 표지 누락·도착 뒤 잔여 거리 표시를 Android와 웹에 함께 반영했습니다. 새 모의 주행 MP4도 포함합니다. 웹 ZIP은 갤러리에 쓰는 이미지만 담아 검증 스크린샷 증가로 배포 파일이 불필요하게 커지지 않도록 했습니다.

0.10.3은 [26개 지역 글로벌 모의 주행](WORLD_DRIVE_KO.md)에서 발견한 조기 도착 안내 문구를 수정했습니다. 새 스위스 산악도로 재검증 영상(18.76초, 실제 브라우저·합성 GPS)과 서명 APK를 제공합니다.

0.10.4는 [장시간 글로벌 모의주행](WORLD_SOAK_KO.md), [실제 가게 검색부터 도착까지의 연속 영상과 GPS 불확실성 안내 영상](VIDEO_REVIEW_V0104.md)을 제공합니다. 현재 브라우저 회귀 검사는 날짜변경선 8개를 포함한 132개입니다.

0.10.4 ZIP은 공개 파일의 Unix 권한을 `644`로 저장하며 일반 압축 해제 후 권한·파일 내용 일치를 확인했습니다. Pages 워크플로는 빌드·검사·배포 파일 보관과 사이트 배포를 별도 작업으로 나눕니다. 당시 소유자 활성화가 필요했던 상태는 위 0.10.5 공개 배포에서 해결됐습니다.

0.10.5: [검색 누락·주변 추천 후속 수정](SEARCH_RECOVERY_KO.md)과 새 연속 녹화를 포함합니다. 공개 Pages 상태는 APK·ZIP 업로드와 별개입니다.
