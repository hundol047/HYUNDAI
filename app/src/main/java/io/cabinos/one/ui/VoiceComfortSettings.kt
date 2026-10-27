package io.cabinos.one.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cabinos.one.platform.AndroidSpeechProvider

@Composable fun VoiceComfortSettings(speech:AndroidSpeechProvider,language:String,enabled:Boolean) {
    val gentle by speech.gentleOutput.collectAsStateWithLifecycle()
    var choosing by remember {mutableStateOf(false)}
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("차분한 말하기 속도",color=White,fontSize=14.sp)
            Text("목소리 높낮이를 유지하는 차분한 읽기 속도입니다.",color=Muted,fontSize=12.sp)
        }
        Switch(gentle,{speech.setGentleOutput(it)},enabled=enabled)
    }
    ActionButton("목소리 골라 듣기",{choosing=true},Modifier.heightIn(min=56.dp),enabled=enabled)
    if(!enabled)Text("안내를 종료하고 정차한 뒤 목소리를 바꿔 주세요.",color=Muted,fontSize=12.sp)
    if(choosing&&enabled) {
        val voices=remember(language) {speech.outputVoices(language)}
        var selected by remember(language) {mutableStateOf(speech.selectedVoiceName(language)?.takeIf {name->voices.any {it.name==name}})}
        fun choose(name:String?) {
            if(speech.chooseOutputVoice(language,name)) {selected=name;speech.previewOutput(language)}
        }
        AlertDialog(onDismissRequest={choosing=false},title={Text("편안한 목소리를 골라 주세요")},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                Text("선택 시 실제 안내를 들어보는 기능입니다. 이 언어에 설치된 오프라인 목소리 목록입니다.")
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable {choose(null)},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected==null,{choose(null)});Text("자동 선택")
                }
                voices.forEachIndexed {index,voice ->
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable {choose(voice.name)},verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected==voice.name,{choose(voice.name)})
                        Text("목소리 ${index+1} · ${voice.locale.getDisplayName(voice.locale)}")
                    }
                }
                if(voices.size<=1)Text("더 많은 목소리는 기기 음성 설정에서 음성팩을 설치하거나 엔진을 바꿔 확인해 주세요.")
            }},confirmButton={TextButton(onClick={choosing=false}){Text("완료")}})
    }
}
