@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cabinos.core.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun ActiveSessionPanel(s:AppState,vm:CabinViewModel) {
    val session=s.session ?: return
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val preset by vm.audioPreset.collectAsStateWithLifecycle()
    var taskText by remember { mutableStateOf("") }
    val color=when(session.kind){SessionKind.FOCUS->Blue;SessionKind.RELAX->Mint;SessionKind.AUDIO->Purple}
    Row(Modifier.fillMaxWidth().heightIn(min=275.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.58f).height(316.dp).clip(RoundedCornerShape(20.dp)).border(1.dp,color.copy(alpha=.45f),RoundedCornerShape(20.dp))) {
            MountainScene(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Row {Pill("${session.kind.label} 모드",color);Spacer(Modifier.weight(1f));Text(if(session.status==SessionStatus.PAUSED)"일시정지됨" else "나를 위한 시간",fontSize=11.sp,color=White)}
                Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(25.dp)) {
                    Box(Modifier.size(150.dp),contentAlignment=Alignment.Center) {
                        CircularProgressIndicator(progress={session.remainingSeconds.toFloat()/session.durationSeconds.coerceAtLeast(1)},color=color,trackColor=White.copy(alpha=.12f),strokeWidth=4.dp,modifier=Modifier.fillMaxSize())
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(timeText(session.remainingSeconds),fontSize=34.sp,color=White,fontWeight=FontWeight.Light)
                            Spacer(Modifier.height(6.dp));Text(if(session.kind==SessionKind.RELAX) {if((s.nowMs/4000)%2==0L)"천천히 들이쉬세요" else "편안하게 내쉬세요"} else "지금, 이 순간에 집중",fontSize=11.sp,color=White.copy(alpha=.7f))
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(if(session.kind==SessionKind.RELAX)"잠시, 쉬어가도 괜찮아요." else if(session.kind==SessionKind.FOCUS)"당신만의 몰입을 시작하세요." else "좋은 소리로 채우는 시간.",color=White,fontSize=17.sp,fontWeight=FontWeight.Medium)
                        Text("출발 준비 ${s.preferences.departureBufferMinutes}분 전 자동 종료\n남은 일정에 맞춰 시간을 조정해요.",color=White.copy(alpha=.65f),fontSize=11.sp)
                        SessionActionButtons(s,{vm.engine.pauseOrResume()},{vm.engine.endSession()})
                    }
                }
                AmbientControls(vm,session.status==SessionStatus.RUNNING)
            }
        }
        SurfacePanel(Modifier.weight(.42f).height(316.dp)) {
            if(session.kind==SessionKind.FOCUS) {
                Row {SectionLabel("나의 할 일",color=Blue);Spacer(Modifier.weight(1f));Text("${tasks.count{it.done}} / ${tasks.size}",color=Muted,fontSize=11.sp)}
                FocusTaskPresets(s.parkedAllowed,vm::addTask)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(enabled=s.parkedAllowed,value=taskText,onValueChange={taskText=it.take(100)},placeholder={Text("이번 시간에 할 일",fontSize=11.sp)},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(10.dp),textStyle=LocalTextStyle.current.copy(fontSize=12.sp))
                    IconButton(onClick={vm.addTask(taskText);taskText=""},enabled=s.parkedAllowed&&taskText.isNotBlank()){Icon(Icons.Outlined.Add,"할 일 추가",tint=Blue)}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if(tasks.isEmpty()) Text("작은 목표 하나를 적어보세요.\n완료한 일은 다음에도 기억해요.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=20.dp))
                    tasks.forEach { task ->Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=task.done,onCheckedChange={vm.toggleTask(task)},colors=CheckboxDefaults.colors(checkedColor=Blue))
                        Text(task.text,color=if(task.done)Muted else White,fontSize=12.sp,modifier=Modifier.weight(1f))
                        IconButton(onClick={vm.deleteTask(task)}){Icon(Icons.Outlined.Close,"할 일 삭제",tint=Muted,modifier=Modifier.size(15.dp))}
                    } }
                }
            } else {
                SectionLabel("출발까지의 시간",color=color)
                Spacer(Modifier.height(16.dp));Text(timeText(s.schedule.waitingSeconds),fontSize=38.sp,fontWeight=FontWeight.Light,color=White)
                Spacer(Modifier.height(12.dp));Text("${s.schedule.availableSeconds/60}분 뒤 활동 정리",color=Mint,fontSize=13.sp)
                Spacer(Modifier.height(12.dp));HorizontalDivider(color=Line);Spacer(Modifier.height(16.dp))
                Text(if(session.kind==SessionKind.RELAX)"4초 들숨, 4초 날숨\n편안한 호흡으로 다음 여정을 준비하세요." else "기기에서 생성하는 배경 소리입니다.\n실제 스트리밍·차량 오디오 제어는 연결되지 않았습니다.",fontSize=12.sp,color=Muted)
                Spacer(Modifier.weight(1f));Text("StopSense와 시간 동기화 중",fontSize=10.sp,color=color)
            }
        }
    }
}

@Composable fun HistoryScreen(s:AppState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("나를 위한 시간의 기록",fontSize=27.sp,color=White,fontWeight=FontWeight.SemiBold)
        Text("이 기기에만 보관하는 집중·휴식·오디오 세션",color=Muted,fontSize=12.sp)
        FlowRow(maxItemsInEachRow=if(LocalCompactWindow.current)1 else 3,horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SessionKind.entries.forEach { kind ->SurfacePanel(Modifier.weight(1f)) {
                Text("${kind.label} 누적",fontSize=12.sp,color=Muted)
                Spacer(Modifier.height(12.dp));Text("${s.history.filter{it.kind==kind}.sumOf{(it.durationSeconds-it.remainingSeconds).coerceAtLeast(0)}/60}분",fontSize=30.sp,color=if(kind==SessionKind.FOCUS)Blue else if(kind==SessionKind.RELAX)Mint else Purple)
            } }
        }
        if(s.history.isEmpty()) SurfacePanel(Modifier.fillMaxWidth()) {Icon(Icons.Outlined.History,null,tint=Muted);Spacer(Modifier.height(12.dp));Text("아직 완료한 세션이 없어요.",color=White);Text("첫 세션을 시작하면 이곳에 기록됩니다.",color=Muted,fontSize=12.sp)}
        s.history.forEach { session ->SurfacePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text("${session.kind.label} 세션",color=White,fontSize=16.sp);Spacer(Modifier.height(5.dp));Text(SimpleDateFormat("M월 d일 HH:mm",Locale.KOREA).format(Date(session.startedAtMs)),color=Muted,fontSize=11.sp)}
                Text("${(session.durationSeconds-session.remainingSeconds).coerceAtLeast(0)/60}분 진행",color=Mint,fontSize=15.sp)
                Spacer(Modifier.width(20.dp));Pill(if(session.status==SessionStatus.COMPLETED)"완료" else "중단",if(session.status==SessionStatus.COMPLETED)Mint else Amber)
            }
        } }
    }
}
@Composable fun SettingsScreen(s:AppState,vm:CabinViewModel,onNotifications:()->Unit) {
    var confirmDeletion by remember { mutableStateOf(false) }
    val storage by vm.storageStatus.collectAsStateWithLifecycle()
    val voiceAvailable by vm.speech.voiceAvailable.collectAsStateWithLifecycle()
    if(confirmDeletion) AlertDialog(onDismissRequest={confirmDeletion=false},
        title={Text("개인 데이터를 삭제할까요?")},
        text={Text("할 일, 세션 기록, 개인 설정, 출발 계획을 삭제합니다. 진행 중인 세션도 종료됩니다. 차량 데이터 모드 선택은 유지합니다.")},
        confirmButton={TextButton(onClick={confirmDeletion=false;vm.clearPersonalData()}){Text("삭제",color=Red)}},
        dismissButton={TextButton(onClick={confirmDeletion=false}){Text("취소")}})
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("당신에게 맞춘 CabinOS",fontSize=27.sp,color=White,fontWeight=FontWeight.SemiBold)
        Text("개인 설정은 이 기기에 저장됩니다.",color=Muted,fontSize=12.sp)
        AppearanceSettingsPanel(vm)
        FlowRow(maxItemsInEachRow=if(LocalCompactWindow.current)1 else 2,horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SurfacePanel(Modifier.weight(1f)) {
                SectionLabel("시간 프리셋",color=Blue);Spacer(Modifier.height(16.dp))
                SettingStepper("기본 집중 시간",s.preferences.focusMinutes,listOf(15,25,45)){vm.preferences(s.preferences.copy(focusMinutes=it))}
                SettingStepper("기본 휴식 시간",s.preferences.relaxMinutes,listOf(10,15,30)){vm.preferences(s.preferences.copy(relaxMinutes=it))}
                SettingStepper("출발 준비 여유",s.preferences.departureBufferMinutes,listOf(3,5,10)){vm.preferences(s.preferences.copy(departureBufferMinutes=it))}
            }
            SurfacePanel(Modifier.weight(1f)) {
                SectionLabel("음성 및 개인정보");Spacer(Modifier.height(16.dp))
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("음성 안내 · 내비 언어 별도 선택",color=White,fontSize=14.sp);Text(if(voiceAvailable)vm.speech.status else "음성 서비스 사용 불가 · 명령 버튼으로 이용하세요",color=Muted,fontSize=10.sp)};Switch(s.preferences.voiceEnabled,{vm.preferences(s.preferences.copy(voiceEnabled=it))})}
                Spacer(Modifier.height(16.dp));HorizontalDivider(color=Line);Spacer(Modifier.height(16.dp))
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("급감속 후보 공유 동의",color=White,fontSize=14.sp);Text("EchoRoad 서버 연결·이번 연결의 공유 동의가 모두 필요",color=Muted,fontSize=10.sp)};Switch(s.preferences.shareHazards,{vm.preferences(s.preferences.copy(shareHazards=it))})}
                Spacer(Modifier.height(20.dp));Text("동의하고 EchoRoad 서버를 연결하면 급감속 후보의 좌표·방향·시각·감속량을 전송합니다. 원시 주행 이력은 전송하지 않으며 서버는 제보를 15분 후 삭제합니다.",fontSize=11.sp,color=Muted)
            }
        }
        SurfacePanel(Modifier.fillMaxWidth()) {
            SectionLabel("알림과 데이터 관리",color=Blue)
            Spacer(Modifier.height(12.dp))
            Text(if(vm.notificationsAllowed()) "출발 준비 알림 허용됨" else "알림 권한 또는 시스템 알림 설정을 확인해 주세요.",fontSize=13.sp,color=White)
            Text("저장한 출발 계획을 시스템 알림으로 안내합니다. 절전 상태에 따라 늦어질 수 있으며, 재부팅·강제 중지 후에는 다시 앱을 열어 주세요.",fontSize=11.sp,color=Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ActionButton("알림 권한 확인",onNotifications,color=Blue)
                ActionButton("개인 데이터 삭제",{confirmDeletion=true},color=Red)
            }
            storage?.let { Text(it,fontSize=12.sp,color=Amber,modifier=Modifier.padding(top=12.dp)) }
        }
        SurfacePanel(Modifier.fillMaxWidth()) {
            SectionLabel("연결 및 지원 범위",color=Amber);Spacer(Modifier.height(14.dp))
            Text("속도·기어: AAOS 차량 읽기 어댑터 / 일반 기기 데모\nPleos Vehicle SDK: SOC·충전 상태·선택적 실내 정보 읽기\n충전·배터리: 차량 제공 값 / 일반 기기 데모\nNaviHelper: 기본 내비 연결·도착 계획 / FusedLocation: 미연동\n차량 공조 · 조명 제어: 지원 안 함\n음성: Android TTS · SpeechRecognizer (기기 서비스와 언어팩 필요)\nAI 어시스턴트: 오프라인 한국어 명령 해석 · 생성형 AI 미연결",color=Muted,fontSize=12.sp,lineHeight=23.sp)
        }
    }
}
@Composable private fun SettingStepper(label:String,value:Int,options:List<Int>,onSelect:(Int)->Unit) {
    Column {Text(label,color=White,fontSize=13.sp);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ options.forEach { n ->FilterChip(selected=value==n,onClick={onSelect(n)},label={Text("${n}분",fontSize=12.sp)}) } };Spacer(Modifier.height(12.dp))}
}

@Composable internal fun FocusTaskPresets(parked:Boolean,onAdd:(String)->Unit) {
    if(LocalCompactWindow.current) {
        FlowRow(Modifier.fillMaxWidth().testTag("focus-presets"),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            FocusPresetButtons(parked,onAdd)
        }
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("focus-presets"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FocusPresetButtons(parked,onAdd)
        }
    }
}
@Composable private fun FocusPresetButtons(parked:Boolean,onAdd:(String)->Unit) {
    listOf("메일 확인","통화 정리","일정 확인","스트레칭").forEach { task ->
        OutlinedButton(onClick={onAdd(task)},enabled=parked,modifier=Modifier.heightIn(min=48.dp)) { Text(task) }
    }
}
