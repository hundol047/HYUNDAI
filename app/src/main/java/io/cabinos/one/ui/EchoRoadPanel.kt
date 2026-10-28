package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.cabinos.one.platform.*

@Composable
internal fun EchoRoadPanel(ui: NavigationUi, nav: LiveNavigation, canPlan: Boolean) {
    var open by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("https://hyundai-production-1222.up.railway.app") }
    var token by remember { mutableStateOf("") }
    var share by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(ui.echoConnected) { if(ui.echoConnected)token="" }
    Row {
        Text(ui.echoNotice, color = if(ui.echoConnected) Mint else Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        if (canPlan) TextButton(onClick = { error="";open = true }) { Text(if(ui.echoConnected)"EchoRoad 연결됨" else "EchoRoad 연결") }
    }
    ui.echoAhead?.let { Text(it, color = Amber, fontSize = 14.sp) }
    if (open && canPlan)
        AlertDialog(
            onDismissRequest = {
                token = ""
                open = false
            },
            title = { Text("EchoRoad 위험 정보") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(
                        "연결 확인에는 위치를 보내지 않습니다. Railway JSON의 긴 키 부분만 입력하세요. 중괄호·따옴표·device-01은 제외합니다. 키는 앱 종료 시 지워집니다.",
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        endpoint,
                        { endpoint = it.take(300);error="" },
                        enabled = !ui.echoChecking,
                        label = { Text("HTTPS 서버 주소") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        token,
                        { token = it.take(256);error="" },
                        enabled = !ui.echoChecking,
                        label = { Text("기기 접근키") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Row {
                        Checkbox(share, { share = it },enabled=!ui.echoChecking)
                        Text("이번 연결에서 급감속 후보 공유에 동의", fontSize = 13.sp)
                    }
                    Text(
                        "공유 항목: 발생 좌표·방향·시각·감속량. 서버 보관 15분. 2개 이상 기기 제보를 합쳐 표시하며 사고 확정 정보는 아닙니다. 앱 화면 밖에서는 수집·전송을 중지합니다.",
                        fontSize = 12.sp,
                    )
                    if(ui.echoChecking)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(ui.echoNotice,color=if(ui.echoConnected) Mint else Amber)
                    Text("주변 제보 조회 시 현재 좌표가 전송됩니다. 연결 성공과 사고 자료 적재 여부는 별개입니다.",fontSize=12.sp)
                    Text(error, color = Amber)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !ui.echoChecking,
                    onClick = {
                        error=""
                        runCatching { nav.configureEcho(endpoint, token, share) }

                            .onFailure { error = "HTTPS 주소와 24자 이상 기기 접근키를 확인하세요." }
                    }
                ) {
                    Text(if(ui.echoChecking)"확인 중…" else "연결 확인")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        nav.configureEcho("", "")
                        token = "";share = false;error=""
                    }) { Text("연결 해제") }
                    TextButton(onClick = { token="";open=false }) { Text("닫기") }
                }
            },
        )
}
