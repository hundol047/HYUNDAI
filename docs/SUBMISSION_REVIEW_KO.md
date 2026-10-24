# 제출 검토표 — 0.11.0

현재 **공식 SDK 포함 release APK의 빌드·서명은 완료**, 공식 Pleos 실행·영상과 CI 최종 확인은 진행 중입니다. main 병합은 승인 전 수행하지 않습니다. 과거 0.11.0-rc1은 SDK 없는 AAOS 후보였으며 이 문서와 구분합니다.

## 완료된 항목

| 항목 | 검증 결과 |
|---|---|
| 공식 SDK 컴파일 | Vehicle 2.3.2 / NaviHelper 3.0.5 실제 AAR, `-PpleosSdk=true` debug/release 성공 |
| 핵심 기능 | SDK SOC 목표 → 활동 종료, 충전 스위치 → 충전 예산 유효성, NaviHelper 목적지 시간 → 도착 목표 기반 출발 마감. [API 매핑](SDK_CAPABILITIES.md) |
| 안전 제한 | 0 + P 1.5초, 신호 만료 시 잠금, D/0 잠금. 차량 기기 데모 거부. DO 미선언 |
| 테스트 | 공통 131/131, Android 101/101, 녹화 검사 5/5, 선택적 장소 게이트웨이 11/11. 실제 차량 신호 시험과 구분 |
| 린트 | 오류 0, 기존 경고 4 |
| release | versionName 0.11.0 / versionCode 17, 최적화·비디버그 APK, 기존 팀 키 서명·zipalign 검사 |
| 파일 | `apk/퓨처랩스_CabinOS_ONE_release.apk`, `apk/SHA256SUMS` |
| 문서 | README 필수 4항목·라이선스 표·main clone, SDK 핵심 기능 표, 로컬 PC 실행·녹화 명령 |
| 공식 이미지 | Pleos Connect 2.1.0 / API 34 다운로드, 배포 인덱스 SHA-1 대조·설치 완료 |

APK SHA-256: `050b99491ef62218f43aa114ffe6860bcd98dd6dcac5d51d390d4345c5468db4`.

팀 서명 인증서 SHA-256: `33ebb1d6c0e297bb734bb85f55d05edf6faf66375a9448ae2e73a3457e9bb9e0`. 키·비밀번호는 저장소에 포함하지 않습니다.

실행 명령:

```bash
./gradlew -PpleosSdk=true :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
python3 -m unittest discover -s scripts/tests -v
python3 -m unittest discover -s server -v
sha256sum -c apk/SHA256SUMS
```

JVM SDK 테스트는 실제 AAR를 로드합니다. 실제 Vehicle 서비스가 없는 Robolectric에서는 bind 실패를 설정하고 SDK의 비동기 재연결 작업이 다른 테스트에 남지 않도록 클래스별 JVM을 격리합니다. 이를 실제 차량 연결 성공으로 계산하지 않습니다. [기계 판독 기록](validation/submission-0.11.0.json).

## 아직 제출 완료로 표시하지 않는 조건

- 같은 release APK를 공식 Pleos 에뮬레이터에 설치하여 P/0 → D/0 → D/주행 → SOC/충전 변화·계획 갱신을 실제 서비스로 확인.
- 60~120초 연속 MP4를 직접 검토하고 로그인·만료 없는 공개 링크에 게시. Actions artifact는 최종 링크로 쓰지 않음.
- 최신 커밋 CI 결과 확인. PR #1은 main 병합 전 사용자 승인 필요.
- GitHub 소유 계정이 주소에 표시되는 블라인드 제출 문제는 별도 주최 측 확인 필요. 계정 이전·기존 커밋 이력 재작성은 하지 않음.

현재 클라우드는 `/dev/kvm`이 없어 소프트웨어 에뮬레이션의 속도·안정성 한계가 있습니다. 공식 이미지 확보와 실제 부팅/앱 실행 성공을 혼동하지 않습니다. [로컬 PC 재현 명령](PLEOS_VEHICLE_STATE_KO.md).
