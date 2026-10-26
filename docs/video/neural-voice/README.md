# 실제 APK의 신경망 음성 녹화

2026-10-26 · 퓨처랩스 CabinOS ONE · Android 13 에뮬레이터. 아래 영상은 **실제 APK 화면과 Android 출력 소리를 동시에 연속 녹화**했습니다. 외부 내레이션·대체 음원을 넣지 않았습니다.

| 언어 | 영상 | 확인 범위 |
|---|---|---|
| 한국어 | [재생·다운로드](ko-KR/CabinOS_ONE_android_apk.mp4) | 모의 서울 GPS + 실제 OSRM 경로의 회전 안내, UI 검사 7건 |
| 영어 | [재생·다운로드](en-US/CabinOS_ONE_android_apk.mp4) | 앱 설정에서 300m 우회전 미리듣기, UI 검사 3건 |
| 일본어 | [재생·다운로드](ja-JP/CabinOS_ONE_android_apk.mp4) | 앱 설정에서 300m 우회전 미리듣기, UI 검사 3건 |
| 프랑스어 | [재생·다운로드](fr-FR/CabinOS_ONE_android_apk.mp4) | 앱 설정에서 300m 우회전 미리듣기, UI 검사 3건 |
| 독일어 | [재생·다운로드](de-DE/CabinOS_ONE_android_apk.mp4) | 앱 설정에서 300m 우회전 미리듣기, UI 검사 3건 |

한국어는 영상 약 52초, 나머지 언어는 약 32~34초부터 안내가 들립니다. 음소거를 해제하세요.

## 동일 APK와 음성 출처

- APK SHA-256: `c3cae04a7a80692db6ed133253e2e8e0c73131273df29bf4212023c922bfbe9a`
- [APK·설치 QR](../../../preview/README.md), [영상 SHA256SUMS](SHA256SUMS).
- [실행 워크플로](https://github.com/hundol047/HYUNDAI/actions/runs/40328553990): 5개 언어 작업 성공, UI 19건 및 실제 오디오 구간 5건 통과.
- 녹화 기기에 sherpa-onnx 1.13.8 / Supertonic 3 int8을 **별도 설치**했습니다. CabinOS에 모델이 포함되지는 않습니다. 휴대폰의 목소리는 기본 TTS 엔진과 설치한 음성팩에 따라 달라집니다. [설정 방법·출처·라이선스](../../NATURAL_VOICE_KO.md).
- 공개 MP4는 브라우저가 재생을 빨리 시작하도록 컨테이너 메타데이터만 앞쪽으로 옮겼습니다. 오디오·영상 패킷 SHA-256이 원본과 동일함을 각 언어의 `container-verification.json`으로 확인했습니다. 원본 시간 정밀도를 유지한 전체 디코딩 검사도 통과했습니다 (`-fps_mode passthrough -enc_time_base:v 1:1000000`).

## 검증과 제한

각 언어 폴더에 `evidence.json`, `audio-verification.json`, `speech-analysis.json`을 제공합니다. 실제 음성 구간 내부의 최장 저음량 간격은 한국어 0.36초, 나머지 0.04~0.08초였습니다. 이는 자동 음량 검사이며 말의 자연스러움 점수가 아닙니다. 한국어 전사는 ‘약 1,100m 앞에서 유턴하세요. 한강대로 방면입니다.’입니다. 자동 전사는 오인식할 수 있어 원음·예상 문장과 구분해야 합니다.

에뮬레이터의 신경망 합성에는 수 초의 시작 대기가 관측됐습니다. 문장 중간 끊김을 줄이는 수정과 별개로, 실제 기기에서 합성 지연·스피커·운전 중 안내 적시성을 추가 확인해야 합니다. 삼성 S20+, Pleos 실기기, 사람의 청취 평가나 전 세계 모든 언어의 자연스러움을 검증한 영상은 아닙니다. 주행 안전 잠금은 그대로 유지합니다.

[최종 APK·QR 커밋의 Android CI](https://github.com/hundol047/HYUNDAI/actions/runs/40328700000) 성공. 최종 공식 SDK 포함 빌드·lint 성공, Kotlin/Android 테스트 440개와 도구 테스트 60개 통과. 별도 종합 녹화 작업에서 좌표 검색 결과를 찾지 못한 자동화 실패가 있어 재실행 중이며, 이 페이지의 5개 음성 녹화 성공과 구분합니다.
