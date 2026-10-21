# 검증 결과

팀 **퓨처랩스** · CabinOS ONE `0.2.0`.

## 최종 통과

| 검사 | 결과 | 범위 |
|---|---|---|
| `:core:test` | **47개 통과, 실패/스킵 0** | 상태/충돌/주차 디바운스/오래된 신호/신호 복구/출처 변경/역순 신호, 공유 일정, 세션 중단·복원, 위험 점수·필터·쿨다운, 한국어 명령 안전 제한, 백그라운드 신뢰 초기화·삭제 제한·통합 시나리오 |
| `:app:testDebugUnitTest` | **11개 통과, 실패/스킵 0** | 실제 Android Activity의 Compose 전환·정차/신호 대기 제한·설정 화면 유지·집중 시작·주행 복귀, Room 트랜잭션/설정/이력/일시정지 체크포인트 복원, 모드 복원, 알림 토큰·권한·취소, 이력 보관·삭제 |
| `:app:assembleDebug` | 통과 | 개발용 APK 생성 |
| `:app:assembleRelease` | 통과 | R8 코드 최적화·리소스 축소, release 변형 |
| `:app:lintDebug` | 통과 | 오류 0, 고정 가로 화면에 대한 `DiscouragedApi` 경고 1건 |
| `apksigner verify --verbose --print-certs` | 통과 | 전용 3072-bit RSA 팀 릴리스 인증서, APK v3 서명 |
| `zipalign -c -P 16 4` | 통과 | APK ZIP 정렬. 별도 실차/모든 native ELF 페이지 크기 호환성 검증을 뜻하지 않음 |
| APK manifest 확인 | 통과 | `io.cabinos.one`, minSdk 29, targetSdk 35, **debuggable=false** |

총 **58개 JVM/Android 테스트 통과**, **브라우저 기능 검사 15개 통과**. 상세 집계는 [test-results.json](validation/test-results.json), APK 크기/체크섬/인증서는 [apk.json](validation/apk.json), 파일 체크섬은 [apk/SHA256SUMS](../apk/SHA256SUMS)에 있습니다.

브라우저 검사는 실제 Chromium에서 공유 Kotlin/JS 엔진을 로드해 실행했습니다. 입력 제출/Enter·문자 이스케이프, 주행 명령 차단, D 기어 정지, 주차 디바운스, 일정 단축, 일시정지와 출발 타이머 분리, 어시스턴트 열린 중 주행 전환, 설정 적용, 미연결 실차 UNKNOWN, 실제 브라우저 오프라인, APK 다운로드 바이트 일치, 390px 모바일 배치, 실제 MP4 재생과 탐색을 확인했습니다. 결과: [browser-results.json](validation/browser-results.json).

최종 실행 명령:

```bash
source scripts/env.sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
./scripts/package-submission.sh
./scripts/build-web.sh
node scripts/web/verify.cjs
node scripts/web/record-demo.cjs
```

## UI 렌더링 근거

Compose 실제 화면을 Robolectric 네이티브 그래픽 모드(API 29, 1280×800)에서 렌더링했습니다. 동작 검증 후 Activity의 실제 View를 Canvas로 캡처했습니다. 별도의 HTML, 가짜 화면, AI 합성 이미지가 아닙니다. **실차 또는 에뮬레이터 스크린샷으로 표기하지 않습니다.**

- [정차 대시보드](screenshots/parked.png): StopSense와 CabinOS 동시 표시.
- [집중 화면](screenshots/focus.png): 공유 출발/활동 가능 시간, 실제 세션 카운트다운, 할 일, 일시정지/종료/오디오 제어.
- [주행 화면](screenshots/driving.png): EchoRoad 우선, 정차용 조작 제한.

집중 중에는 StopSense를 요약 바에 유지해 타이머·조작부가 화면 아래로 가려지지 않도록 했습니다. 타이머 갱신 시 내비게이션 그래프가 재생성되지 않는지도 앱 테스트에서 확인했습니다.

## 기기/실차 검증의 한계

현재 머신에는 `/dev/kvm`이 없어 Android API 35 에뮬레이터를 소프트웨어 CPU 모드로 구동했습니다. Debug APK 설치와 Activity/프로세스 실행은 확인했지만 Android System UI 응답 지연과 빈 UIAutomation root로 장치 UI 스모크 검증을 완료하지 못했습니다. 안정 채널 에뮬레이터에서도 Android system_server의 SIGABRT가 관찰되어 이 머신의 장치 실행 검증은 중단했습니다. `scripts/device_smoke.py` 실행은 이 환경에서 **통과하지 않았으며**, 위 58개 테스트에 포함하지 않습니다. Release APK의 실제 장치 전체 실행 시나리오도 아직 통과했다고 주장하지 않습니다.

에뮬레이터 설치 성공, Robolectric UI 동작, 실제 차량 동작은 각각 다른 검증입니다. 심사 환경/하드웨어 가속 에뮬레이터에서 서명된 제출 APK를 설치하고 장치 스모크 검사를 추가 수행해야 합니다.

한국어 TTS/STT는 실제 Android API 어댑터를 제공하지만 해당 언어팩/인식 서비스가 있는 실제 기기에서의 음성 품질·서비스 동작은 미검증입니다. 앱 테스트의 한국어 명령 라우팅은 로컬 엔진을 검증합니다.

Pleos 공식 문서/SDK 접근은 프록시 403으로 차단되어 실차 SDK, 실제 충전/위험/경로/POI 데이터 및 공모전 실차 호환성은 **미검증·미연동**입니다. 관련 제약은 [SDK_CAPABILITIES.md](SDK_CAPABILITIES.md)와 [LIMITATIONS.md](LIMITATIONS.md)에 명시했습니다. GitHub Actions 워크플로는 작성했으며 원격 CI 성공 여부는 로컬 테스트 결과와 별개입니다.

## 실행 영상과 재점검

[최종 MP4](video/CabinOS_ONE_demo.mp4)는 실제 Chromium 조작을 CDP로 녹화하고 한국어 자막을 넣었습니다. 그림을 이어 붙인 모형 영상이 아닙니다. 초안 영상에서 할 일 버튼 제출 실패를 확인했고 관련 폼 처리, 주행 상태 문구, 한 번 조작하는 위험 안내와 연결 미확인 화면을 수정한 뒤 다시 촬영했습니다. [영상 검토 내역](VIDEO_REVIEW.md)에 근거와 수정 사항을 기록했습니다. 영상의 Android 화면은 실제 Compose 렌더링을 보여주는 갤러리이며 APK의 실기기 실행 영상은 아닙니다.
