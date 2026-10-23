# 웹 체험·Android 앱·실행 영상

같은 페이지에서 직접 조작하는 브라우저 데모, 실제 Android Compose UI 갤러리, 제출용 APK 다운로드, 실행 MP4를 제공합니다. 브라우저와 Android는 `core`의 Kotlin 상태 엔진을 공유합니다. 실차 연결은 미지원 상태로 명확하게 표시합니다.

## Android·웹 0.10.4 배포 파일

[GitHub Release](https://github.com/hundol047/HYUNDAI/releases/tag/v0.10.4)에서 전용 팀 인증서로 서명한 APK와 [`web-release/CabinOS_ONE_web_v0.10.4.zip`](../web-release/CabinOS_ONE_web_v0.10.4.zip)의 버전별 저장소 다운로드 링크를 제공합니다. 클라우드 프록시가 `uploads.github.com` 연결을 HTTP 403으로 차단해 Release 첨부파일 업로드는 완료되지 않았으며, 허용된 기존 Git 저장소에 배포 파일을 보관합니다. 웹 ZIP에는 인터랙티브 데모·Android UI 갤러리·APK·기존 MP4·새 모의 주행 MP4가 포함됩니다. HTTPS 정적 호스팅에 압축을 풀어 배포해야 하며 ZIP 다운로드 자체가 사이트 공개를 뜻하지 않습니다. GitHub Release에는 자동 24시간 만료를 설정하지 않았습니다.

음성 길찾기와 언어 선택은 [사용법과 실제 지원 범위](VOICE_NAVIGATION_KO.md)를 확인하세요. 기존 MP4는 0.5.0 녹화로 새 음성 기능의 녹화가 아닙니다.

## 공개 접속 상태

GitHub Pages 배포 워크플로와 정적 사이트 구성을 준비했습니다. **현재 공개 사이트 배포 성공은 확인되지 않았습니다.** 예상 배포 주소는 `https://hundol047.github.io/HYUNDAI/`이며, 배포 성공 전에는 동작하는 링크로 간주하면 안 됩니다.

저장소 소유자가 한 번 [Settings → Pages](https://github.com/hundol047/HYUNDAI/settings/pages)에서 **Build and deployment → Source → GitHub Actions**를 선택해야 합니다. 이후 main 푸시 또는 [Publish CabinOS ONE demo](https://github.com/hundol047/HYUNDAI/actions/workflows/pages.yml)의 Run workflow로 배포합니다. Actions의 `deploy` 성공 후 표시되는 URL을 확인하세요.

환경 재시작 후 GitHub API 조회와 공개 주소 접근은 가능해졌습니다. 저장소 조회에서 `has_pages=false`를 확인했고, 공개 주소는 HTTP 404입니다. 2026-10-23에도 Pages 활성화를 다시 요청했지만 생성 API는 연동 앱 권한 제한으로 `403 Resource not accessible by integration`을 반환했습니다. 저장소 소유자가 위 설정에서 Pages를 활성화해야 합니다.

[이전 원격 웹 검사](https://github.com/hundol047/HYUNDAI/actions/runs/39638061596)에서 사이트 빌드와 브라우저 기능 검사는 통과했으며, `configure-pages` 단계에서 Pages 미활성화로 HTTP 404가 발생했습니다. 필요한 도메인과 재사용 시작 절차는 클라우드 환경 설정 초안에 저장했습니다. 초안 저장은 사이트 배포를 의미하지 않습니다.

GitHub Pages는 이 작업 환경의 서버 종료와 관계없이 유지되며 24시간 만료를 설정하지 않았습니다. 실제 가용 시간은 GitHub 서비스와 저장소 설정에 따릅니다.

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

0.10.4 ZIP은 공개 파일의 Unix 권한을 `644`로 저장하며 일반 압축 해제 후 권한·파일 내용 일치를 확인했습니다. Pages 워크플로는 빌드·검사·배포 파일 보관과 사이트 배포를 별도 작업으로 나눕니다. 소유자의 Pages 활성화가 필요한 상태는 그대로 표시합니다.
