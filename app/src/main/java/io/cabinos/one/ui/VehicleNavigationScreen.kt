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
            OutlinedTextField(query,{query=it.take(180)},Modifier.fillMaxWidth(),label={Text("가게명·주소·도시")},singleLine=true)
            ActionButton("차량 내비에서 검색",{vm.pleosNavigation.open(VehicleNavigationScreen.SEARCH,query,vm.state.value.parkedAllowed)},Modifier.fillMaxWidth(),enabled=navigation.connected&&query.isNotBlank())
        }
        listOf("기본 내비게이션 열기" to VehicleNavigationScreen.MAP,"주변 충전소 찾기" to VehicleNavigationScreen.CHARGERS,"최근 목적지" to VehicleNavigationScreen.RECENT).forEach { (label,screen) ->
            ActionButton(label,{vm.pleosNavigation.open(screen,parked=vm.state.value.parkedAllowed)},Modifier.fillMaxWidth(),enabled=navigation.connected&&s.parkedAllowed)
        }
        Text("길 안내·교통·지도 제공 범위는 차량에 설치된 내비게이션을 따릅니다. 출발 후에는 기본 내비게이션에서 안내를 이어갑니다.",color=Muted,fontSize=13.sp)
    }
}
