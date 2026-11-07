# Pleos 실제 차량 검증 — 2026-11-07

[방법·결과·제한](../../PLEOS_SETUP_VERIFICATION_KO.md) · [판정 요약](result.json) · [원본 이벤트](raw-evidence.json) · [수행 동작](actions.json) · [연속 원본 영상](device-screen.mp4)

[실행 42361658349](https://github.com/hundol047/HYUNDAI/actions/runs/42361658349)의 13개 검사는 모두 통과했습니다. 영상은 173.85초, 2560×1440, 227프레임이며 편집·합성하지 않았습니다. 원격 소프트웨어 그래픽의 지연이 포함돼 있습니다. 세션 상태 RUNNING → PAUSED → COMPLETED는 원본 SDK/앱 이벤트로 확인합니다. 부드러운 집중 화면 표시·공식 GPU 성능·최종 제출 완료를 주장하지 않습니다.

- [D/0 SDK 수신 직후 앱 잠금](02-drive-zero.png): 계기판 표시는 지연될 수 있어 기어 판정은 실제 SDK 로그와 함께 확인합니다.
- [주행 중 OS 차단 화면](03-moving.png)
- [SOC 80% 목표 도달·활동 예산 0](05-soc-target.png)
- [백그라운드 복귀](06-resume.png)

원본 로그 묶음은 [Actions artifact](https://github.com/hundol047/HYUNDAI/actions/runs/42361658349/artifacts/13569897469)에 있으며 보존 기간은 7일입니다. 이 디렉터리의 원본 영상·이벤트·요약·화면은 저장소에 보존합니다. 자동 생성 원본의 `visualReview=pending`, `submissionReady=false`는 그대로 유지하고 별도 result.json에 확인 범위를 기록했습니다.
