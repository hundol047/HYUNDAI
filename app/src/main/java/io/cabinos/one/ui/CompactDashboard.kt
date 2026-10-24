package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cabinos.core.*

val LocalCompactWindow = staticCompositionLocalOf { false }

/** Single-column layout for Pleos one-cell windows and phones; no minimum landscape width. */
@Composable fun CompactParkedDashboard(s: AppState, vm: CabinViewModel, onMap: () -> Unit) {
    var timeDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("이 시간을, 나답게.",color=White,fontSize=26.sp)
        Pill(UnifiedVehicleContextEngine.contextLabel(s.context))
        SurfacePanel(Modifier.fillMaxWidth(),accent=Mint) {
            Text(if(s.sample?.charging==true) "충전 중" else "나만의 정차 시간",color=Mint,fontSize=18.sp)
            Text("${(s.schedule.waitingSeconds+59)/60}분 남음",color=White,fontSize=36.sp)
            Text(s.schedule.estimateLabel,color=Muted,fontSize=12.sp)
            val battery=s.sample?.batteryPercent ?: -1
            Text(if(battery>=0) "배터리 $battery% · ${if(s.sample?.source==DataSource.SIMULATION) "데모" else "차량 신호"}" else "배터리 정보 없음",color=Muted)
            Spacer(Modifier.height(12.dp))
            Text("활동 ${s.schedule.availableSeconds/60}분 · 출발 준비 ${s.preferences.departureBufferMinutes}분 확보",color=Mint)
            ActionButton("시간 설정",{timeDialog=true},Modifier.fillMaxWidth())
        }
        val cabin by vm.cabin.state.collectAsStateWithLifecycle()
        CabinAdvicePanel(s,cabin) {vm.start(SessionKind.RELAX,s.preferences.relaxMinutes)}
        val session=s.session?.takeIf {it.status==SessionStatus.RUNNING || it.status==SessionStatus.PAUSED}
        if(session!=null) CompactSession(s,vm,session) else {
            listOf(SessionKind.FOCUS to s.preferences.focusMinutes,SessionKind.RELAX to s.preferences.relaxMinutes,SessionKind.AUDIO to 15).forEach { (kind,minutes) ->
                SurfacePanel(Modifier.fillMaxWidth()) {
                    Text("${kind.label} 모드",color=White,fontSize=20.sp)
                    Text("추천 ${minOf(minutes.toLong(),s.schedule.availableSeconds/60)}분",color=Mint)
                    ActionButton("${kind.label} 시작",{vm.start(kind,minutes)},Modifier.fillMaxWidth(),enabled=s.schedule.availableSeconds>0)
                }
            }
        }
        ActionButton(if(vm.automotive) "차량 내비게이션에서 주변 찾기" else "지도에서 주변 찾기",onMap,Modifier.fillMaxWidth())
        VehiclePlanControls(s,vm)
        Text(s.message,color=Muted,fontSize=12.sp)
    }
    if(timeDialog) WaitingDialog(s,{timeDialog=false},{vm.waiting(it);timeDialog=false})
}

@Composable private fun CompactSession(s: AppState,vm: CabinViewModel,session: CabinSession) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val audio by vm.audioPreset.collectAsStateWithLifecycle()
    var task by remember { mutableStateOf("") }
    SurfacePanel(Modifier.fillMaxWidth(),accent=Blue) {
        Text("${session.kind.label} 세션",color=Mint,fontSize=22.sp)
        Text(timeText(session.remainingSeconds),color=White,fontSize=52.sp)
        Text("출발 준비 ${s.preferences.departureBufferMinutes}분 전 자동 종료",color=Muted,fontSize=13.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ActionButton(if(session.status==SessionStatus.RUNNING) "일시정지" else "계속",{vm.engine.pauseOrResume()},Modifier.weight(1f))
            ActionButton("종료",{vm.engine.endSession()},Modifier.weight(1f),color=Muted)
        }
        if(session.kind==SessionKind.RELAX) Text(if((s.nowMs/4000)%2==0L) "천천히 들이쉬세요" else "편안하게 내쉬세요",color=Mint)
        listOf("백색 소음","자연의 소리","잔잔한 톤").forEach { name ->
            ActionButton(if(audio==name) "$name 중지" else name,{vm.toggleAudio(name)},Modifier.fillMaxWidth(),enabled=session.status==SessionStatus.RUNNING)
        }
        if(session.kind==SessionKind.FOCUS) {
            FocusTaskPresets(s.parkedAllowed,vm::addTask)
            OutlinedTextField(task,{task=it.take(100)},Modifier.fillMaxWidth(),enabled=s.parkedAllowed,label={Text("이번 시간에 할 일")},singleLine=true)
            ActionButton("할 일 추가",{vm.addTask(task);task=""},Modifier.fillMaxWidth(),enabled=s.parkedAllowed&&task.isNotBlank())
            tasks.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(item.done,{vm.toggleTask(item)})
                    Text(item.text,Modifier.weight(1f).padding(vertical=12.dp),color=White)
                    TextButton(onClick={vm.deleteTask(item)}){Text("삭제")}
                }
            }
        }
    }
}
