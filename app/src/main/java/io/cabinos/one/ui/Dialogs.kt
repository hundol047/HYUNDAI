package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cabinos.core.*

@Composable fun WaitingDialog(s:AppState,onDismiss:()->Unit,onSet:(Int)->Unit) {
    var minutes by remember { mutableIntStateOf(((s.schedule.waitingSeconds+59)/60).toInt().coerceIn(5,120)) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("나의 정차 시간")},text={Column {
        Text("출발까지 남은 시간을 설정하세요. 차량 충전·도착 계획과 비교해 더 이른 마감을 사용합니다.",fontSize=13.sp,color=Muted)
        Spacer(Modifier.height(20.dp));Text("${minutes}분",color=Mint,fontSize=36.sp)
        Slider(value=minutes.toFloat(),onValueChange={minutes=it.toInt()},valueRange=5f..120f,steps=22)
        Text("출발 준비 ${s.preferences.departureBufferMinutes}분을 제외하고 세션을 추천합니다.",fontSize=12.sp,color=Muted)
    }},confirmButton={TextButton(onClick={onSet(minutes)}){Text("적용")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
@Composable fun AssistantDialog(s:AppState,vm:CabinViewModel,onClose:()->Unit,onMicrophone:()->Unit) {
    val reply by vm.reply.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val commands=if(s.parkedAllowed) listOf("충전 얼마나 남았어?","25분 집중 모드 켜줘","15분 휴식 모드 시작해 줘","근처 쉴 곳 추천해 줘","출발 5분 전에 알려줘","이제 출발할게") else listOf("앞에 위험 구간 있어?","현재 주행 상황 알려줘")
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        SurfacePanel(Modifier.widthIn(max=700.dp).fillMaxWidth(.88f).heightIn(max=600.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.GraphicEq,null,tint=Blue,modifier=Modifier.size(26.dp));Spacer(Modifier.width(12.dp));Text("CabinOS 어시스턴트",fontSize=20.sp);Spacer(Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"닫기")}}
            Text("오프라인 명령 해석 · 차량 안전 규칙 적용",fontSize=11.sp,color=Muted)
            Spacer(Modifier.height(20.dp))
            SurfacePanel(Modifier.fillMaxWidth(),accent=Blue) {Text(reply,color=White,fontSize=17.sp,lineHeight=27.sp)}
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                commands.chunked(2).forEach { row ->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach {text->ActionButton(text,{vm.command(text)},Modifier.weight(1f),Blue)}} }
                if(s.parkedAllowed) Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(value=input,onValueChange={input=it.take(100)},modifier=Modifier.weight(1f),placeholder={Text("한국어로 입력하세요",fontSize=13.sp)},singleLine=true,shape=RoundedCornerShape(12.dp))
                    IconButton(onClick={vm.command(input);input=""},enabled=input.isNotBlank()){Icon(Icons.AutoMirrored.Outlined.Send,"명령 실행",tint=Blue)}
                }
            }
            Spacer(Modifier.height(12.dp));ActionButton("말하기",onMicrophone,Modifier.fillMaxWidth(),Blue,icon=Icons.Outlined.MicNone)
            Text("${vm.speech.status} · 음성 인식 서비스가 없으면 명령 버튼을 사용하세요.",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=9.dp))
        }
    }
}
@Composable fun SimulatorDialog(s:AppState,vm:CabinViewModel,onClose:()->Unit,onLocation:()->Unit,onVehiclePermissions:()->Unit={}) {
    val selected by vm.scenario.collectAsStateWithLifecycle()
    val vehicleStatus by vm.vehicleStatus.collectAsStateWithLifecycle()
    val pleosState by vm.pleosState.collectAsStateWithLifecycle()
    val energyState by vm.pleosEnergy.collectAsStateWithLifecycle()
    val running by vm.demoRunning.collectAsStateWithLifecycle()
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        SurfacePanel(Modifier.widthIn(max=830.dp).fillMaxWidth(.93f).heightIn(max=680.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Science,null,tint=Amber);Spacer(Modifier.width(10.dp));Text(if(vm.automotive)"차량 신호 상태" else "차량 연결 / 시뮬레이터",fontSize=22.sp);Spacer(Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"닫기")}}
            Text(if(selected=="실차 연결")"$vehicleStatus · 읽기 전용" else "데모 모드 — 시뮬레이션 데이터 · 실제 차량을 제어하지 않습니다.",fontSize=12.sp,color=Amber)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row {Pill(UnifiedVehicleContextEngine.contextLabel(s.context),Blue);Spacer(Modifier.width(10.dp));Pill("기어 ${s.sample?.gear?.name ?: "알 수 없음"}",Muted);Spacer(Modifier.width(10.dp));Pill("${s.sample?.speedKph?.toInt() ?: 0} km/h",Muted)}
                if(!vm.automotive && selected!="실차 연결") {
                listOf(listOf("주행","신호 대기","주차","충전"),listOf("충전 15분","출발 준비","SDK 끊김","실차 연결")).forEach { row ->Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach {name->ActionButton(name,{vm.selectScenario(name)},Modifier.weight(1f),if(selected==name)Mint else Muted)}} }
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    ActionButton("위험 이벤트 주입",{vm.engine.injectHazard()},Modifier.weight(1f),Red)
                    ActionButton(if(s.networkAvailable)"네트워크 끊기" else "네트워크 복구",{vm.engine.setConnectivity(network=!s.networkAvailable)},Modifier.weight(1f),Blue)
                    ActionButton(if(s.locationAvailable)"위치 거부" else "위치 복구",{vm.engine.setConnectivity(location=!s.locationAvailable)},Modifier.weight(1f),Blue)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    ActionButton("집중 시작",{vm.start(SessionKind.FOCUS,25)},Modifier.weight(1f),Blue,s.parkedAllowed)
                    ActionButton("휴식 시작",{vm.start(SessionKind.RELAX,15)},Modifier.weight(1f),Mint,s.parkedAllowed)
                    ActionButton(if(running)"시나리오 중지" else "전체 시나리오 실행",{vm.runDemo()},Modifier.weight(2f),Amber)
                }
                }
                if(selected=="실차 연결") {
                    if(!vm.automotive) ActionButton("데모로 돌아가기",{vm.selectScenario("충전")},Modifier.fillMaxWidth(),Amber)
                    Text(energyState.message + (energyState.percent?.let { " · $it%" } ?: ""),fontSize=12.sp,color=Muted)
                    Text(pleosState.message,fontSize=12.sp,color=if(pleosState.brakePressed!=null)Mint else Amber)
                    pleosState.brakePressed?.let { pressed ->
                        val age=(s.nowMs-(pleosState.successfulReadAtMs ?: 0)).coerceAtLeast(0)/1000
                        Text("브레이크 페달: ${if(pressed)"눌림" else "해제"} · ${age}초 전 SDK 읽기 · P 판정과 별개",fontSize=11.sp,color=Muted)
                    }
                    Text("AAOS CarPropertyManager · 속도 / 기어 읽기. 위치만으로 주차를 판단하지 않습니다. 배터리·충전은 지원되는 AAOS 속성 또는 Pleos EvBattery를 읽습니다.",fontSize=11.sp,color=Amber)
                    ActionButton("차량 서비스 다시 연결",{vm.selectScenario("실차 연결")},Modifier.fillMaxWidth(),Blue)
                    if(vm.automotive) ActionButton("차량 읽기 권한 확인",onVehiclePermissions,Modifier.fillMaxWidth(),Blue)
                    ActionButton("실제 위치 권한 허용",onLocation,Modifier.fillMaxWidth(),Blue,s.parkedAllowed)
                }
                Text("P 상태가 1.5초 이상 확인되어야 정차 기능이 활성화됩니다. 신호 대기는 D 기어를 유지합니다. 전체 시나리오는 창을 닫아도 계속됩니다.",fontSize=11.sp,color=Muted)
                HorizontalDivider(color=Line)
                Text("중앙 엔진 이벤트",fontSize=13.sp,color=White)
                s.events.takeLast(6).reversed().forEach { e ->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.KOREA).format(java.util.Date(e.timestampMs)),color=Mint,fontSize=10.sp)
                    Text(e.message,color=Muted,fontSize=11.sp)
                } }
            }
            Spacer(Modifier.height(12.dp));ActionButton("대시보드로 돌아가기",onClose,Modifier.fillMaxWidth(),Mint)
        }
    }
}
