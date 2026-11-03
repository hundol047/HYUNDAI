# 안내 말투·발음 보완 후 실제 APK 녹화

2026-10-26 · 퓨처랩스 CabinOS ONE. 같은 검토용 APK를 Android 13 에뮬레이터에서 실행해 **화면과 실제 Android 출력 소리를 연속 녹화**했습니다.

| 언어 | 음성 구간 바로 듣기 | 전체 실행 영상 | 자동 전사로 대조한 내용 |
|---|---|---|---|
| 한국어 | [M4A](ko-KR/voice-sample.m4a) | [MP4](ko-KR/CabinOS_ONE_android_apk.mp4) | 약 1.1km 앞에서 유턴입니다. 한강대로 방면입니다. |
| 영어 | [M4A](en-US/voice-sample.m4a) | [MP4](en-US/CabinOS_ONE_android_apk.mp4) | In 300 meters, turn right. |
| 일본어 | [M4A](ja-JP/voice-sample.m4a) | [MP4](ja-JP/CabinOS_ONE_android_apk.mp4) | 300メートル先、右に曲がります。 |
| 프랑스어 | [M4A](fr-FR/voice-sample.m4a) | [MP4](fr-FR/CabinOS_ONE_android_apk.mp4) | Dans trois cents mètres, tournez à droite. |
| 독일어 | [M4A](de-DE/voice-sample.m4a) | [MP4](de-DE/CabinOS_ONE_android_apk.mp4) | In 300 Meter rechts abbiegen. |

M4A는 해당 MP4의 음성 검사 구간을 추출한 파일입니다. 앞부분에는 실제 합성 대기 시간이 포함됩니다. 전체 MP4에는 자르기·이어 붙이기·대체 내레이션을 적용하지 않았습니다. 브라우저 재생 시작을 돕는 MP4 메타데이터 이동만 적용하고 원본과 영상·음성 패킷 해시가 같은지 확인했습니다.

## 무엇을 확인했나

- [녹화 작업](https://github.com/hundol047/HYUNDAI/actions/runs/40338994062): 5개 언어 성공, UI 검사 19건과 실제 오디오 구간 5건 통과.
- 한국어는 모의 서울 GPS와 실제 OSRM 경로 안내, 나머지는 앱 설정의 300m 우회전 미리듣기입니다. Pleos·실제 차량·현장 GPS 시연이 아닙니다.
- 각 폴더의 `evidence.json`에 설치 APK 해시·기기·검사 결과가 있습니다. `audio-verification.json`은 실제 소리 유무 검사, `speech-analysis.json`은 자동 전사입니다. 일본어·프랑스어·독일어는 별도 인식 모델의 `second-transcription.json`도 제공합니다.
- 첫 수정 녹화에서 프랑스어 숫자와 일본어 축약 표현의 전사가 불안정해 읽을 형태의 숫자·문장으로 보완하고 다시 녹화했습니다. 최종 프랑스어와 일본어의 거리·방향은 두 인식 모델에서 일치했습니다.
- 모든 영상의 전체 디코딩을 검사했습니다. [미디어 SHA256SUMS](SHA256SUMS).

## APK와 목소리

- APK SHA-256: `aba15205f8043dd979e993fa4f2108b192a6dbd840717e18aa2d092f14464edf`
- [설치 APK·QR](../../../preview/README.md) · [수정 내용·공식 문서 비교 범위](../../VOICE_GUIDANCE_REVIEW_KO.md) · [빌드·테스트 기록](../../../preview/voice-quality-verification.json)
- 녹화 기기에는 **sherpa-onnx 1.13.8 / Supertonic 3 int8**을 별도로 설치했습니다. [엔진 설정·출처·라이선스](../../NATURAL_VOICE_KO.md). CabinOS APK만 설치하면 같은 목소리가 자동으로 추가되는 것은 아닙니다.
- 실제 휴대폰에서는 기본 TTS 엔진과 설치한 음성팩에 따라 음색·억양·속도가 다릅니다. 앱 설정의 **안내 음성 들어보기 / 기기 음성 설정**으로 확인할 수 있습니다.

## 남은 검증

자동 전사는 사람의 청취 평가가 아니며, 31개 언어의 문장 테스트도 모든 언어의 자연스러움을 보증하지 않습니다. 이번 녹화의 음성 시작에는 대략 3~5초가 걸렸습니다. 8초 합성 대기 취소는 오래된 안내를 제한하는 처리이며, 지연 개선이나 운전 중 안내의 적시성 인증이 아닙니다. 실제 S20+·차량의 엔진·스피커·지연과 원어민 청취 평가는 별도로 필요합니다.
