# 장소별 사진 · 실제 APK 실행 영상

[연속 실행 영상 보기/다운로드](CabinOS_ONE_place_photos.mp4) · [실행 증거](evidence.json) · [영상 SHA-256](SHA256SUMS)

Android 13 에뮬레이터에서 배포한 휴대폰 검토 APK를 설치한 뒤 `adb shell screenrecord`로 87.83초 연속 촬영했습니다. 사진을 이어 붙인 영상이 아닙니다. 영상 전체 디코딩을 검사했고 카페·공원·쇼핑 화면을 직접 확인했습니다. 음성 트랙은 없습니다.

- APK SHA-256: `0a8e41dde13b560e2228e97f6cc896b78df386051dfce9d8bae5e3374831dd38`
- 패키지: `io.cabinos.one.preview`, 0.11.1-preview. 설치된 APK를 다시 추출해 배포 파일과 해시를 대조했습니다.
- 차량 상태는 앱 데모이고 위치는 파리의 에뮬레이터 모의 위치입니다. 실제 차량·현장 GPS 검증이 아닙니다. 장소 검색과 사진은 실제 공개 서비스에서 조회했습니다.
- Café de Flore, Les Deux Magots, Square Laurent Prache의 서로 다른 실제 사진 표시를 확인했습니다. 쇼핑 장소의 공개 사진 미확인 상태도 그대로 담았습니다.
- 자동 확인 4개 통과. 첫 촬영은 두 번째 항목을 스크롤하지 못해 중단됐고, 목록 영역 스크롤을 수정한 후 이 영상을 다시 촬영했습니다.
- 모든 매장 사진 확보, 실제 영업 상태, 실제 Pleos 차량 실행을 입증하는 영상은 아닙니다. [사진 조회·출처·제한](../../PLACE_PHOTOS_KO.md)

[녹화 실행 기록](https://github.com/hundol047/HYUNDAI/actions/runs/40273910025) — 영상 자체는 만료되는 Actions artifact 대신 이 저장소에 보관합니다.
