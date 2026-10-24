package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cabinos.core.AppState
import io.cabinos.one.platform.VehicleNavigationScreen

@Composable fun VehicleNavigationPanel(s: AppState, vm: CabinViewModel) {
    val navigation by vm.pleosNavigation.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Pleos 차량 내비게이션",color=White,fontSize=26.sp)
        Text("목적지를 정하고, 다음 여정을 준비하세요.",color=Muted)
        SurfacePanel(Modifier.fillMaxWidth()) {
            Text(navigation.message,color=if(navigation.connected)Mint else Amber)
            if(navigation.included && !navigation.connected)
                ActionButton("다시 연결",{vm.pleosNavigation.stop();vm.pleosNavigation.start()},Modifier.fillMaxWidth())
            if(!navigation.included) Text("이 APK에는 NaviHelper가 포함되어 있지 않습니다. SDK 포함 차량용 빌드가 필요합니다.",color=Muted)
        }
        if(s.parkedAllowed) {
            SurfacePanel(Modifier.fillMaxWidth()) {
                Text("도착 목표에 맞춘 출발",color=White)
                val fresh=navigation.readAtMs?.let { s.nowMs-it in 0..10_000 } == true
                Text(if(fresh) "${navigation.destination} · ${(navigation.remainingSeconds!!+59)/60}분 · ${navigation.remainingMeters}m" else "차량 내비게이션에서 경로를 설정하면 남은 시간과 거리를 받습니다.",color=Muted)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(30,60,120).forEach { minutes ->
                        OutlinedButton(onClick={vm.arrival(minutes)},enabled=fresh) { Text("${minutes}분 뒤 도착") }
                    }
                }
                if(s.arrivalTargetMs!=null) {
                    Text("도착 목표 ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(requireNotNull(s.arrivalTargetMs)))}",color=Mint)
                    Text("경로 변경·수신 중단 시 이전 마감을 유지합니다. 새 계획은 도착 목표를 다시 설정하세요.",color=Muted)
                    TextButton(onClick={vm.arrival(null)}) { Text("도착 목표 해제") }
                }
                Text("${s.schedule.estimateLabel} · 활동 ${s.schedule.availableSeconds/60}분 · 출발 준비 ${s.preferences.departureBufferMinutes}분",color=Mint)
            }
            OutlinedTextField(query,{query=it.take(180)},Modifier.fillMaxWidth(),label={Text("가게명·주소·도시")},singleLine=true)
            ActionButton("차량 내비에서 검색",{vm.pleosNavigation.open(VehicleNavigationScreen.SEARCH,query,vm.state.value.parkedAllowed)},Modifier.fillMaxWidth(),enabled=navigation.connected&&query.isNotBlank())
        }
        listOf("기본 내비게이션 열기" to VehicleNavigationScreen.MAP,"주변 충전소 찾기" to VehicleNavigationScreen.CHARGERS,"최근 목적지" to VehicleNavigationScreen.RECENT).forEach { (label,screen) ->
            ActionButton(label,{vm.pleosNavigation.open(screen,parked=vm.state.value.parkedAllowed)},Modifier.fillMaxWidth(),enabled=navigation.connected&&s.parkedAllowed)
        }
        Text("길 안내·교통·지도 제공 범위는 차량에 설치된 내비게이션을 따릅니다. 출발 후에는 기본 내비게이션에서 안내를 이어갑니다.",color=Muted,fontSize=13.sp)
    }
}
