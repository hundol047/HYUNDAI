package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cabinos.core.*

@Composable fun AppearanceSettingsPanel(vm:CabinViewModel) {
    val settings by vm.appearance.collectAsStateWithLifecycle()
    var custom by remember(settings.accent){mutableStateOf(settings.accent)}
    val palette=AppearanceCatalog.palette(settings)
    SurfacePanel(Modifier.fillMaxWidth()) {
        SectionLabel("실내에 맞춘 화면 색상")
        Text("차분한 어두운 배경과 읽기 쉬운 대비를 사용합니다. 변경은 주차 중에만 가능합니다.",fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AppearanceCatalog.presets.forEach {p->
                Column(Modifier.weight(1f).border(if(settings.preset==p.id)2.dp else 1.dp,if(settings.preset==p.id)Mint else Line,RoundedCornerShape(12.dp)).clickable {vm.appearance(settings.copy(preset=p.id))}.padding(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(p.panel,p.accent).forEach {c->Box(Modifier.size(18.dp).background(Color(c or (0xFF shl 24)),RoundedCornerShape(5.dp)))}}
                    Spacer(Modifier.height(8.dp));Text(p.label,color=White,fontSize=12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(custom,{custom=it.take(7)},Modifier.weight(1f),singleLine=true,label={Text("강조색 #RRGGBB")},isError=custom.isNotBlank()&&AppearanceCatalog.hex(custom)==null)
            ActionButton("강조색 적용",{vm.appearance(settings.copy(accent=custom))},color=Mint,enabled=AppearanceCatalog.hex(custom)!=null)
            ActionButton("기본 강조색",{vm.appearance(settings.copy(accent=""))},color=Muted)
        }
        Text("실제 표시 색 ${AppearanceCatalog.format(palette.accent)} · 너무 어두운 색은 글자·버튼 대비를 위해 밝게 보정합니다. 경고·오류 색은 유지합니다.",color=Muted,fontSize=11.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("system" to "시스템 밝기","comfort" to "편안한 밝기","night" to "야간 밝기").forEach {(key,label)->FilterChip(selected=settings.brightness==key,onClick={vm.appearance(settings.copy(brightness=key))},label={Text(label)})}
        }
        Row {Text("야간 지도 · 지도 배경만 어둡게",Modifier.weight(1f),color=White,fontSize=13.sp);Switch(settings.nightMap,{vm.appearance(settings.copy(nightMap=it))})}
        Text("밝기는 앱 창에만 적용합니다.",color=Muted,fontSize=11.sp)
    }
}
