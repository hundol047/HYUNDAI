# Railway에서 EchoRoad 실행하기

이 저장소의 `railway.toml`은 Android 앱 전체 대신 `server/Dockerfile.echo`의 Python 공유 서버만 빌드합니다. 앱 APK는 바뀌지 않습니다. 기본 데이터셋은 비어 있으며, 업로드된 CSV를 아직 자동으로 서비스하지 않습니다.

1. Railway의 HYUNDAI **서비스** → Settings → Source에서 배포 브랜치를 `fix/vehicle-runtime-issues`로 선택합니다. main에는 이 설정이 아직 없습니다. Root Directory는 저장소 루트(`/`)로 유지합니다. 기존 사용자 지정 Build Command·Start Command가 있다면 이 Dockerfile과 충돌하지 않는지 확인합니다.
2. 서비스 → Variables에 `CABIN_ECHO_TOKENS_JSON`을 추가합니다. 값은 **기기별 무작위 접근키 → 익명 기기 ID**의 JSON 객체입니다. 모든 이용자에게 같은 키를 배포하면 안 됩니다. 첫 검토용 휴대폰에는 `device-01` 같은 ID를 사용합니다.
3. 자신의 PC에서 다음 명령으로 JSON을 생성하고, 출력 전체를 Railway 변수 값에 붙입니다. 실제 출력은 채팅·GitHub에 올리지 마세요. 앱에는 JSON의 긴 무작위 키 부분만 입력합니다.

```bash
python -c "import secrets,json; print(json.dumps({secrets.token_urlsafe(32): 'device-01'}))"
```

4. 변경 사항을 Deploy합니다. Railway의 `PORT`를 서버가 읽고, 컨테이너는 `0.0.0.0`으로 수신합니다. 별도의 Android SDK·API 키는 이 공유 서버 시작에 필요하지 않습니다. 인증 변수 없이 시작하면 실패하도록 유지했습니다.
5. 배포가 정상 상태가 되면 Settings → Networking → Public Networking → **Generate Domain**으로 HTTPS 주소를 생성합니다. 화면 명칭은 Railway UI 버전에 따라 다를 수 있습니다. Target Port를 묻는 경우 실제 서비스의 PORT 값과 맞춥니다.
6. 공개 주소 뒤에 `/healthz`를 붙여 `{"status":"ok"}`가 나오는지 확인합니다. 이 응답은 서버 실행 확인이며 공식 사고 자료 연결이나 실제 사용자 제보의 존재를 뜻하지 않습니다.
7. 앱 내비 → EchoRoad 연결에 **HTTPS 기본 주소**와 기기 접근키를 입력합니다. 급감속 후보 공유는 별도로 동의해야 합니다. 키가 다른 2개 이상 독립 기기의 조건에 맞는 제보가 있어야 공유 위험이 표시됩니다.

검증: Python 서버 테스트 20개 통과, TOML 구문 검사 통과. 이 환경에는 Railway 계정 연결이 없어 Railway 원격 배포·컨테이너 실행·공개 HTTPS 확인은 아직 하지 못했습니다. 플랫폼 TLS를 사용하며 이 컨테이너를 TLS 없는 주소로 앱에 연결하지 않습니다. 메모리 집계를 사용하므로 인스턴스는 1개로 두고, 재배포·재시작 시 제보가 초기화됩니다. 요금·사용량 제한은 Railway 계정에서 확인하세요.

## 받은 사고 자료

[파일별 검사 결과](ACCIDENT_UPLOAD_AUDIT.json). 지자체별 5,977행, 보행자 2,402행, 어린이 1,300행, 결빙 685행입니다. 좌표·Polygon/MultiPolygon 구조 검사 오류는 0건이며 실제 도로 위치 정확성 검증은 아닙니다. 별도 지역별 통계 46,764행은 좌표가 없어 지점 안내로 사용할 수 없습니다.

좌표 자료에는 명시적 통계 연도 열이 없습니다. `사고다발지id`의 앞 네 자리를 통계 연도로 간주하면 안 됩니다. 예를 들어 파일명은 2025년까지인데 `2026...` ID가 존재합니다. 유형별 통계 기준연도와 데이터셋 ID의 공식 대응 관계, 최신 자료 선택, MultiPolygon 처리와 기존 5,000행 한도 처리가 필요하므로 현재 서버에 임의 적재하지 않았습니다. 파일 방식은 API 키 없이 가공·연결할 수 있지만 새 자료 갱신은 별도 다운로드가 필요합니다.
