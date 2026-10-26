package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import io.cabinos.one.platform.*

@Composable
internal fun EchoRoadPanel(ui: NavigationUi, nav: LiveNavigation, canPlan: Boolean) {
    var open by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var share by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Row {
        Text(ui.echoNotice, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        if (canPlan) TextButton(onClick = { open = true }) { Text("EchoRoad 연결") }
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
                Column {
                    Text(
                        "주변 제보 조회 시 현재 좌표를 전송합니다. 개별 기기에 발급된 접근키가 필요합니다. 키는 앱 종료 시 지워집니다.",
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        endpoint,
                        { endpoint = it.take(300) },
                        label = { Text("HTTPS 서버 주소") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        token,
                        { token = it.take(256) },
                        label = { Text("기기 접근키") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Row {
                        Checkbox(share, { share = it })
                        Text("이번 연결에서 급감속 후보 공유에 동의", fontSize = 13.sp)
                    }
                    Text(
                        "공유 항목: 발생 좌표·방향·시각·감속량. 서버 보관 15분. 2개 이상 기기 제보를 합쳐 표시하며 사고 확정 정보는 아닙니다. 앱 화면 밖에서는 수집·전송을 중지합니다.",
                        fontSize = 12.sp,
                    )
                    Text(error, color = Amber)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { nav.configureEcho(endpoint, token, share) }
                            .onSuccess {
                                token = ""
                                open = false
                            }
                            .onFailure { error = "HTTPS 주소와 24자 이상 기기 접근키를 확인하세요." }
                    }
                ) {
                    Text("연결")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nav.configureEcho("", "")
                        token = ""
                        share = false
                        open = false
                    }
                ) {
                    Text("연결 해제")
                }
            },
        )
}
