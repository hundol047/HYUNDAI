package io.cabinos.one.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.cabinos.core.AppState
import io.cabinos.core.DataSource

/** Planning preference only. Never changes the vehicle's charging target or controls. */
@Composable fun VehiclePlanControls(s: AppState, vm: CabinViewModel) {
    if(s.sample?.source != DataSource.VEHICLE) return
    SurfacePanel(Modifier.fillMaxWidth()) {
        Text("충전 후 출발 계획",color=White)
        Text(if(s.sample?.pleosSoc==true) "Pleos SOC ${s.sample?.batteryPercent}% · 목표 도달 시 활동 종료" else "Pleos SOC 수신 대기 · 목표 자동 종료 대기",color=Muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf<Int?>(null,80,90,100).forEach { target ->
                FilterChip(selected=s.chargeTargetPercent==target,onClick={vm.engine.setChargeTarget(target)},
                    label={Text(target?.let { "$it%" } ?: "목표 해제")},enabled=s.parkedAllowed)
            }
        }
        Text("활동 종료 목표입니다. 차량의 충전 설정은 바꾸지 않으며, SOC만으로 남은 충전 시간을 추정하지 않습니다.",color=Muted)
    }
}
