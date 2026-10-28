> 이전 APK의 eSpeak 녹화입니다. [최신 APK의 신경망 음성 영상 5편](../neural-voice/README.md)을 먼저 확인하세요.

# 실제 앱 소리를 포함한 실행 영상

최신 휴대폰 검토 APK를 설치해 scrcpy로 **화면과 Android 출력 오디오를 동시에 연속 녹화**했습니다. 배경음 파일을 영상에 따로 붙이거나 내레이션을 사후 합성하지 않았습니다. APK와 앱 코드는 이번 녹화 작업에서 바꾸지 않았습니다.

- [집중 세션 · 배경음 6종 · 2분 19초](CabinOS_ONE_ambience_audio.mp4): 백색 소음 → 자연의 소리(새) → 잔잔한 톤 → 비 → 계곡 → 파도 → 소리 끄기.
- [내비게이션 · 한국어 음성 안내 · 1분 15초](CabinOS_ONE_navigation_audio.mp4): 목적지 검색 → 실제 OSRM 경로 → 확대 지도 → 지도 이동·복귀 → 안내 다시 듣기 → 안내 종료·홈.

## 확인 결과

[실행 CI 통과](https://github.com/hundol047/HYUNDAI/actions/runs/40319738957) · [검증 요약](verification.json) · [파일 SHA-256](SHA256SUMS).

- 설치 APK 화면 조작 15건 통과: 배경음 8건, 내비 7건.
- 실제 녹음 구간 8건 통과: 배경음 6종, 중지 후 무음, 한국어 내비 반복 안내. [배경음 측정](ambience-audio-verification.json) · [내비 측정](navigation-audio-verification.json).
- 두 MP4의 영상·오디오 전체 디코딩을 확인했습니다. H.264 영상과 AAC 오디오가 들어 있습니다.
- 초기 녹화는 출력 경로 음량이 작아 새소리 검사를 통과하지 못했습니다. scrcpy 오디오 캡처 경로 생성 **후** 에뮬레이터 미디어 음량을 적용해 해결했습니다. 공개한 최종 파일은 촬영 원본 그대로이며 사후 음량 증폭·음원 교체·영상 자르기를 하지 않았습니다. 별도로 시험한 음량 보정 파일은 공개본에 사용하지 않았습니다.

배경음 확인 구간: **0:24 백색 소음 / 0:42 새소리 / 1:00 잔잔한 톤 / 1:18 비 / 1:36 계곡 / 1:56 파도 / 2:10 중지**. 내비 ‘다시 듣기’는 약 **0:48**입니다. 실제 구간은 [배경음](ambience-audio-windows.json)·[내비](navigation-audio-windows.json) 기록을 참고하세요. 이 음량은 녹화용 에뮬레이터 설정이며 실물 스피커 음량이나 청취 편안함을 검증한 수치는 아닙니다.

## 재현 조건

- APK SHA-256: `105dc0fde1627307b1c2d44423abc0bd5cbe6d5869f68723caeda40e310b1437`. 설치 파일을 다시 추출해 원본과 대조했습니다.
- 일반 Android 13 에뮬레이터. 차량 상태는 앱 데모이고, 위치는 서울의 모의 GPS입니다. 실제 차량·Pleos·현장 운전 검증이 아닙니다.
- TTS는 녹화용 에뮬레이터에 설치한 [eSpeak NG 1.52.0](https://github.com/espeak-ng/espeak-ng/releases/tag/1.52.0)입니다. 앱이 Android TTS API로 요청한 한국어 안내를 이 엔진이 재생합니다. 삼성/Google 음성 엔진과 목소리·발음이 다를 수 있습니다.
- 기기 내부 오디오를 녹음했으며 물리 스피커·마이크·음성 인식 검증은 아닙니다. 목적지 입력은 UiAutomator 접근성 입력입니다.
- [기존 무음 실행 영상](../release-review/README.md)은 화면 회전·정차 안전 잠금·장소 사진의 별도 증거로 유지합니다.

## 자연음 출처·라이선스

배경음 영상에는 Effib의 [Sound of rain](https://commons.wikimedia.org/wiki/File:Sound_of_rain.ogg)을 발췌·정규화·페이드 처리한 앱 재생음이 포함됩니다. 배경음 영상은 [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/)으로 공유합니다. 촬영: 퓨처랩스. 계곡은 jackthemurray(CC0), 파도는 Jarrod stanley(CC0), 새소리는 ezwa(Public domain)입니다. [전체 원본·변경 내역](../../NATURE_AUDIO_CREDITS.md).
