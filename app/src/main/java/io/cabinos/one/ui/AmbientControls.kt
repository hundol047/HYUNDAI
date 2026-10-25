package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun AmbientControls(vm:CabinViewModel,enabled:Boolean) {
    val preset by vm.audioPreset.collectAsStateWithLifecycle()
    val status by vm.audioStatus.collectAsStateWithLifecycle()
    var open by remember {mutableStateOf(false)}
    ActionButton(if(preset==null)"배경 소리 선택" else "$preset · 소리 설정",{open=true},enabled=enabled)
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("배경 소리")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("백색 소음","자연의 소리","잔잔한 톤").forEach {name->
                ActionButton(if(preset==name)"■ $name 중지" else "▶ $name",{vm.toggleAudio(name)},Modifier.fillMaxWidth(),enabled=enabled)
            }
            Text(status,color=Muted,fontSize=13.sp)
            Text("기기에서 합성한 소리입니다. 블루투스 연결 중이면 연결된 기기에서 재생됩니다.",color=Muted,fontSize=11.sp)
            ActionButton("휴대폰 미디어 음량",vm::showMediaVolume,Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(onClick={open=false}){Text("완료")}},dismissButton={TextButton(onClick={vm.stopAudio()}){Text("소리 끄기")}})
}
