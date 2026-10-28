# 최신 검토 APK 실제 실행 영상

**[소리 포함 새 실행 영상 2편](../live-audio/README.md)** — 아래 3편은 기존 화면 조작 검증용 무음 영상입니다.

- [내비게이션 · 약 1분 12초](CabinOS_ONE_navigation.mp4): 목적지 입력 → 실제 OSRM 경로 → 자동 전체 화면 → 지도 드래그/현재 위치 복귀 → 낮은 가로 창/큰 글씨 → 세로 전환 → 안내 종료 → 홈 복귀. [9건 통과 기록](navigation-evidence.json).
- [정차·집중 세션 · 약 2분 41초](CabinOS_ONE_runtime.mp4): 정차 플랜, 소리 중지, 시간 연장, 충전 마감, D/0 잠금과 P 복귀. [10건 통과 기록](stop-plan-evidence.json).
- [장소·사진 · 약 1분 18초](CabinOS_ONE_places.mp4): 카테고리 전환과 서로 다른 실제 카페·공원 등록 사진. [8건 통과 기록](photos-evidence.json).

모두 설치된 APK를 Android 13에서 `adb shell screenrecord`로 연속 촬영했습니다. 사진을 이어 붙인 영상이 아닙니다. MP4 전체 해독과 내비 영상의 최종 홈 복귀 프레임을 확인했습니다. 음성 트랙이 없어 스피커 음질 검증은 아닙니다. 내비 영상 후반 세로 화면은 360×800 dp 전체가 고정된 가로 녹화 화면 안에 들어가며 나머지는 검게 표시됩니다.

APK SHA-256: `105dc0fde1627307b1c2d44423abc0bd5cbe6d5869f68723caeda40e310b1437`. 설치 APK를 다시 추출해 공개 다운로드 파일과 동일성을 확인했습니다. [영상 SHA-256](SHA256SUMS).

차량 입력은 앱 데모, GPS는 내비 영상의 서울/사진 영상의 파리 모의 위치입니다. 목적지는 UiAutomator 접근성 텍스트 입력으로 넣었습니다. 실제 키보드 타이핑·마이크·실제 주행·S20+·최신 Pleos 서비스 검증으로 주장하지 않습니다. 지도·OSRM 경로·장소 사진은 실제 외부 서비스와 APK UI를 사용합니다.

[내비 실행](https://github.com/hundol047/HYUNDAI/actions/runs/40302122581) · [정차 실행](https://github.com/hundol047/HYUNDAI/actions/runs/40300756330) · [사진 실행](https://github.com/hundol047/HYUNDAI/actions/runs/40300756222). 영상은 만료되는 Actions artifact 대신 저장소에 보관합니다.

[가로 내비 실제 캡처](navigation-landscape.png) · [세로 홈 복귀 실제 캡처](navigation-portrait.png)
