# 웹 체험·Android 앱·실행 영상

같은 페이지에서 직접 조작하는 브라우저 데모, 실제 Android Compose UI 갤러리, 제출용 APK 다운로드, 한국어 자막 MP4를 제공합니다. 브라우저와 Android는 `core`의 Kotlin 상태 엔진을 공유합니다. 실차 연결은 미지원 상태로 명확하게 표시합니다.

## 공개 접속 상태

GitHub Pages 배포 워크플로와 정적 사이트 구성을 준비했습니다. **현재 공개 사이트 배포 성공은 확인되지 않았습니다.** 예상 배포 주소는 `https://hundol047.github.io/HYUNDAI/`이며, 배포 성공 전에는 동작하는 링크로 간주하면 안 됩니다.

저장소 소유자가 한 번 [Settings → Pages](https://github.com/hundol047/HYUNDAI/settings/pages)에서 **Build and deployment → Source → GitHub Actions**를 선택해야 합니다. 이후 main 푸시 또는 [Publish CabinOS ONE demo](https://github.com/hundol047/HYUNDAI/actions/workflows/pages.yml)의 Run workflow로 배포합니다. Actions의 `deploy` 성공 후 표시되는 URL을 확인하세요.

환경 재시작 후 GitHub API 조회와 공개 주소 접근은 가능해졌습니다. 저장소 조회에서 `has_pages=false`를 확인했고, 공개 주소는 HTTP 404입니다. Pages 생성 API는 연동 앱 권한 제한으로 `403 Resource not accessible by integration`을 반환했습니다. 저장소 소유자가 위 설정에서 Pages를 활성화해야 합니다.

[최신 원격 웹 검사](https://github.com/hundol047/HYUNDAI/actions/runs/39638061596)에서 사이트 빌드와 브라우저 기능 검사는 통과했으며, `configure-pages` 단계에서 Pages 미활성화로 HTTP 404가 발생했습니다. 필요한 도메인과 재사용 시작 절차는 클라우드 환경 설정 초안에 저장했습니다. 초안 저장은 사이트 배포를 의미하지 않습니다.

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
