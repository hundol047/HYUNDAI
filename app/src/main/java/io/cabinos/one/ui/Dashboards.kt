package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cabinos.core.*
import kotlin.math.min

@Composable fun ParkedDashboard(s:AppState,vm:CabinViewModel,onMap:()->Unit) {
    var waitingOpen by remember { mutableStateOf(false) }
    val active=s.session?.let { it.status==SessionStatus.RUNNING || it.status==SessionStatus.PAUSED } == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("이 시간을, 나답게.",fontSize=28.sp,fontWeight=FontWeight.SemiBold,color=White)
                    Spacer(Modifier.width(14.dp));Pill(UnifiedVehicleContextEngine.contextLabel(s.context))
                }
                Spacer(Modifier.height(5.dp));Text("차는 잠시 멈추고, 당신의 시간은 계속됩니다.",fontSize=12.sp,color=Muted)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text("StopSense  +  CabinOS",color=Mint,fontWeight=FontWeight.Medium,fontSize=13.sp)
                Spacer(Modifier.height(5.dp));Text("함께 작동 중 · 안전한 주차 확인",color=Muted,fontSize=10.sp)
            }
        }
        if(active) CompactSchedule(s,onTime={waitingOpen=true})
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            SurfacePanel(Modifier.weight(.34f).fillMaxHeight(),accent=Mint.copy(alpha=.5f)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(s.sample?.charging==true) Icons.Outlined.EvStation else Icons.Outlined.LocalParking,null,tint=Mint,modifier=Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp));Text(if(s.sample?.charging==true)"충전 중" else "나만의 정차 시간",fontSize=13.sp,color=White,fontWeight=FontWeight.Medium)
                    Spacer(Modifier.weight(1f));TextButton(onClick={waitingOpen=true},contentPadding=PaddingValues(0.dp)){Text("시간 설정",fontSize=10.sp,color=Mint)}
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment=Alignment.Bottom) {
                            Text("${(s.schedule.waitingSeconds+59)/60}",fontSize=46.sp,fontWeight=FontWeight.Light,color=White)
                            Text(" 분 남음",fontSize=14.sp,color=Muted,modifier=Modifier.padding(bottom=8.dp))
                        }
                        Text(s.schedule.estimateLabel,fontSize=10.sp,color=Muted)
                    }
                    CarIllustration(Modifier.width(126.dp).height(75.dp))
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress={ (s.sample?.batteryPercent ?: 0).coerceIn(0,100)/100f },modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)),color=Mint,trackColor=Line)
                Spacer(Modifier.height(9.dp))
                Row { Text(if((s.sample?.batteryPercent ?: -1)>=0)"${s.sample?.batteryPercent}%" else "—",color=Mint,fontSize=12.sp,fontWeight=FontWeight.Medium);Text(if(s.sample?.source==DataSource.SIMULATION)"  배터리 · 시뮬레이션" else "  배터리 신호 미연동",color=Muted,fontSize=10.sp);Spacer(Modifier.weight(1f));Text("100%",color=Muted,fontSize=10.sp) }
                Spacer(Modifier.height(16.dp));HorizontalDivider(color=Line)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column {Text("활동 가능",fontSize=10.sp,color=Muted);Spacer(Modifier.height(4.dp));Text("${s.schedule.availableSeconds/60}분",fontSize=22.sp,color=Mint,fontWeight=FontWeight.Medium)}
                    Column(horizontalAlignment=Alignment.End) {Text("출발 준비 여유",fontSize=10.sp,color=Muted);Spacer(Modifier.height(4.dp));Text("${s.preferences.departureBufferMinutes}분 확보",fontSize=13.sp,color=White)}
                }
            }
            ActivityPanel(s,vm,Modifier.weight(.66f).fillMaxHeight(),onMap)
        }
        SectionLabel("CabinOS","지금, 당신에게 필요한 모드",Blue)
        if(active) ActiveSessionPanel(s,vm) else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            ModeCard("집중 모드","작은 몰입이 만드는 큰 차이",min(s.preferences.focusMinutes.toLong(),s.schedule.availableSeconds/60).toInt(),Icons.Outlined.CenterFocusStrong,Blue,Modifier.weight(1f)){vm.start(SessionKind.FOCUS,s.preferences.focusMinutes)}
            ModeCard("휴식 모드","다음 여정을 위한 재충전",min(s.preferences.relaxMinutes.toLong(),s.schedule.availableSeconds/60).toInt(),Icons.Outlined.Spa,Mint,Modifier.weight(1f)){vm.start(SessionKind.RELAX,s.preferences.relaxMinutes)}
            ModeCard("오디오 모드","좋아하는 소리와 함께하는 시간",min(15L,s.schedule.availableSeconds/60).toInt(),Icons.Outlined.Headphones,Purple,Modifier.weight(1f)){vm.start(SessionKind.AUDIO,15)}
        }
        if(s.context==VehicleContext.PREPARING_DEPARTURE) SurfacePanel(accent=Amber) {Text("출발 준비 시간입니다",color=Amber,fontSize=18.sp);Text("활동을 정리하고 주변을 확인해 주세요. 주행을 시작하면 EchoRoad로 자동 전환됩니다.",color=White,fontSize=13.sp)}
        Text(s.message,color=Muted,fontSize=11.sp,modifier=Modifier.padding(bottom=5.dp))
    }
    if(waitingOpen) WaitingDialog(s,onDismiss={waitingOpen=false},onSet={vm.waiting(it);waitingOpen=false})
}

@Composable private fun CompactSchedule(s:AppState,onTime:()->Unit) {
    SurfacePanel(Modifier.fillMaxWidth(),accent=Mint.copy(alpha=.4f)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(22.dp)) {
            Column(Modifier.weight(1f)) {
                SectionLabel("StopSense",color=Mint)
                Spacer(Modifier.height(7.dp))
                Text(if(s.sample?.charging==true)"충전 중 · ${s.sample?.batteryPercent ?: 0}%" else "안전한 주차 확인",fontSize=11.sp,color=Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("출발까지",fontSize=11.sp,color=Muted)
                Text(timeText(s.schedule.waitingSeconds),fontSize=27.sp,color=White,fontWeight=FontWeight.Light)
            }
            Column(Modifier.weight(1f)) {
                Text("활동 가능한 시간",fontSize=11.sp,color=Muted)
                Text(timeText(s.schedule.availableSeconds),fontSize=27.sp,color=Mint,fontWeight=FontWeight.Light)
            }
            Column(Modifier.weight(1f)) {
                Text("출발 준비 ${s.preferences.departureBufferMinutes}분 확보",fontSize=12.sp,color=White)
                Spacer(Modifier.height(6.dp))
                Text(s.schedule.estimateLabel,fontSize=10.sp,color=Muted)
            }
            ActionButton("시간 설정",onTime,color=Mint,icon=Icons.Outlined.Schedule)
        }
    }
}

@Composable private fun ActivityPanel(s:AppState,vm:CabinViewModel,modifier:Modifier,onMap:()->Unit) {
    SurfacePanel(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) { SectionLabel("StopSense","주변에서 보내는 시간");Spacer(Modifier.weight(1f));TextButton(onClick=onMap){Text("지도에서 보기",fontSize=12.sp,color=Mint)} }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("전체","카페","식당","산책","쇼핑").forEach { category ->
                FilterChip(selected=s.preferences.preferredCategory==category,onClick={vm.preferences(s.preferences.copy(preferredCategory=category))},label={Text(category,fontSize=11.sp)},modifier=Modifier.height(36.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Mint.copy(alpha=.16f),selectedLabelColor=Mint),border=FilterChipDefaults.filterChipBorder(enabled=true,selected=s.preferences.preferredCategory==category,borderColor=Line,selectedBorderColor=Mint.copy(alpha=.4f)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            s.activities.take(3).forEach { option ->
                val a=option.activity
                val color=when(a.category){"카페"->Amber;"산책"->Mint;"식당"->Red;else->Purple}
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Night.copy(alpha=.55f)).border(1.dp,Line,RoundedCornerShape(12.dp)).clickable{vm.mapSelect(a.id);onMap()}.padding(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(62.dp).background(Brush.linearGradient(listOf(color.copy(alpha=.15f),Panel)),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                        Icon(when(a.category){"카페"->Icons.Outlined.LocalCafe;"산책"->Icons.Outlined.Park;"식당"->Icons.Outlined.Restaurant;else->Icons.Outlined.Storefront},null,tint=color,modifier=Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(10.dp));Text(a.name,fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=White,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp));Text("도보 약 ${a.oneWaySeconds/60}분 · ${if(a.simulated)"예시" else "OSM"}",fontSize=10.sp,color=Muted,maxLines=1)
                    Spacer(Modifier.height(5.dp));Text(if(option.feasible)"${option.usableSeconds/60}분 이용 가능  ↗" else "시간 부족",fontSize=10.sp,color=if(option.feasible)Mint else Amber)
                }
            }
        }
        Spacer(Modifier.height(12.dp));Text(if(s.schedule.returnSeconds>0)"선택한 장소의 왕복 ${s.schedule.returnSeconds/60}분을 CabinOS 시간에 반영했어요." else "왕복 이동과 출발 준비 시간을 먼저 확보해요.",color=Muted,fontSize=10.sp)
    }
}
@Composable private fun ModeCard(title:String,description:String,minutes:Int,icon:androidx.compose.ui.graphics.vector.ImageVector,color:androidx.compose.ui.graphics.Color,modifier:Modifier,onStart:()->Unit) {
    SurfacePanel(modifier,accent=color.copy(alpha=.55f)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(color.copy(alpha=.16f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=color,modifier=Modifier.size(24.dp))}
            Spacer(Modifier.width(12.dp));Column {Text(title,color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text("추천 ${minutes}분",color=color,fontSize=12.sp)}
        }
        Spacer(Modifier.height(12.dp));Text(description,color=Muted,fontSize=11.sp)
        Spacer(Modifier.height(14.dp));ActionButton("시작하기",onStart,Modifier.fillMaxWidth(),color,minutes>0,Icons.Outlined.PlayArrow)
    }
}

@Composable fun DrivingDashboard(s:AppState,onVoice:()->Unit,onNavigation:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(s.context==VehicleContext.UNKNOWN)"안전한 연결을 기다립니다." else "안전한 여정, 함께할게요.",fontSize=27.sp,fontWeight=FontWeight.SemiBold,color=White)
                Spacer(Modifier.height(5.dp));Text(if(s.context==VehicleContext.UNKNOWN)"차량 상태를 확인하기 전까지 정차 전용 기능을 제한합니다." else "필요한 정보만, 필요한 순간에. EchoRoad가 함께합니다.",fontSize=12.sp,color=Muted)
            }
            Pill(UnifiedVehicleContextEngine.contextLabel(s.context),if(s.context==VehicleContext.UNKNOWN)Amber else Blue)
        }
        if(s.context==VehicleContext.UNKNOWN) {
            SurfacePanel(Modifier.fillMaxWidth().weight(1f),accent=Amber.copy(alpha=.5f)) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.SensorsOff,null,tint=Amber,modifier=Modifier.size(48.dp))
                Spacer(Modifier.height(20.dp))
                Text("차량 상태를 확인할 수 없습니다.",color=White,fontSize=24.sp)
                Spacer(Modifier.height(12.dp))
                Text("현재 속도와 기어, 경로를 표시할 수 없어 정차용 조작을 제한합니다.",color=Muted,fontSize=15.sp)
                Spacer(Modifier.height(10.dp))
                Text(if(s.sample?.source==DataSource.VEHICLE) "차량 상태에서 서비스 연결·속도/기어 읽기 권한을 확인하세요." else "차량 신호가 복구되면 안전 상태를 다시 확인합니다.",color=Amber,fontSize=13.sp)
                Spacer(Modifier.weight(1f))
            }
            return@Column
        }
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(.66f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).border(1.dp,Line,RoundedCornerShape(20.dp))) {
                RouteScene(Modifier.fillMaxSize())
                Column(Modifier.padding(20.dp)) {
                    Pill("EchoRoad · ${if(s.echoRoadActive)"활성" else "안전 제한"}",Blue)
                    Spacer(Modifier.height(12.dp));Text("${s.sample?.speedKph?.toInt() ?: 0}",fontSize=48.sp,fontWeight=FontWeight.Light,color=White);Text(if(s.sample?.source==DataSource.VEHICLE)"km/h · 차량 신호" else "km/h · 시뮬레이션",fontSize=11.sp,color=Muted)
                }
                SurfacePanel(Modifier.align(Alignment.TopEnd).padding(18.dp).width(155.dp),accent=Blue) {
                    Icon(Icons.Outlined.TurnRight,null,tint=Blue,modifier=Modifier.size(28.dp));Text("3.2 km",fontSize=23.sp,color=White);Text("판교IC · 데모 경로",fontSize=11.sp,color=Muted)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent,Night))).padding(20.dp)) {
                    Text(if(s.locationAvailable)"시뮬레이션 경로 · 실제 길 안내 아님" else "위치 사용 불가 · 경로 위험 안내 중단",color=if(s.locationAvailable)Muted else Amber,fontSize=11.sp)
                    Spacer(Modifier.height(10.dp));Row {Pill("12 km   ·   18분",Blue);Spacer(Modifier.weight(1f));ActionButton("내비게이션",onNavigation,color=Blue,enabled=true,icon=Icons.Outlined.Navigation)}
                }
            }
            Column(Modifier.weight(.34f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                SectionLabel("경로 위험 정보",color=Blue)
                s.hazards.take(3).forEachIndexed { index,h ->
                    SurfacePanel(Modifier.fillMaxWidth(),accent=if(index==0) Red.copy(alpha=.6f) else Line) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber,null,tint=if(index==0)Red else Amber,modifier=Modifier.size(24.dp));Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {Text(h.hazard.kind,color=White,fontSize=18.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(4.dp));Text("전방 ${"%.1f".format(h.distanceMeters/1000)} km · 신뢰도 ${(h.hazard.confidence*100).toInt()}%",fontSize=14.sp,color=Muted)}
                        }
                    }
                }
                if(s.hazards.isEmpty()) SurfacePanel {Text(if(!s.locationAvailable)"위치 확인이 필요합니다" else "확인된 위험 정보 없음",color=Muted,fontSize=14.sp)}
                Spacer(Modifier.weight(1f))
                Text("주행 중에는 집중·휴식·활동 추천을 제한합니다. 신호 대기 중에도 주행 안전 모드를 유지합니다.",fontSize=11.sp,color=Muted)
                Text(s.message,color=Muted,fontSize=12.sp,maxLines=3)
                ActionButton("위험 정보 듣기",onVoice,Modifier.fillMaxWidth(),Blue,icon=Icons.Outlined.MicNone)
            }
        }
    }
}
