package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    AmbientQuickActions(preset,enabled,{open=true},vm::stopAudio)
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("배경 소리")},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            io.cabinos.one.platform.AmbientSamples.presets.forEach {name->
                ActionButton(if(preset==name)"■ $name 중지" else "▶ $name",{vm.toggleAudio(name)},Modifier.fillMaxWidth(),enabled=enabled)
                Text(when(name){
                    "백색 소음"->"일정한 소음 · 주변 소리를 가리는 용도"
                    "자연의 소리"->"정원의 새소리 · 실제 녹음"
                    "잔잔한 톤"->"두 음이 이어지는 합성음 · 음악 아님"
                    "빗소리"->"비가 떨어지는 소리 · 실제 녹음"
                    "계곡 소리"->"가까이 흐르는 물소리 · 실제 녹음"
                    else->"밀려오고 잦아드는 파도 · 실제 녹음"
                },color=Muted,fontSize=12.sp)
            }
            Text(status,color=Muted,fontSize=13.sp)
            Text("비·계곡·파도·자연의 소리는 실제 자연음 녹음입니다. 백색 소음·잔잔한 톤은 합성음입니다. 블루투스 연결 중이면 연결된 기기에서 재생됩니다.",color=Muted,fontSize=11.sp)
            Text("빗소리: Effib · CC BY-SA 3.0 · commons.wikimedia.org/wiki/File:Sound_of_rain.ogg · 앞 20초 발췌, 시작 무음 제거·음량·페이드 가공. 계곡: jackthemurray · CC0. 파도: Jarrod stanley · CC0. 새소리: ezwa · Public domain.",color=Muted,fontSize=11.sp)
            ActionButton("미디어 음량 조절",vm::showMediaVolume,Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(onClick={open=false}){Text("완료")}},dismissButton={TextButton(onClick={vm.stopAudio()}){Text("소리 끄기")}})
}
