package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.cabinos.core.AppState
import io.cabinos.one.platform.CabinAdvice

@Composable internal fun CabinAdvicePanel(s:AppState,cabin:CabinAdvice,onRelax:()->Unit) {
    if(!s.parkedAllowed)return
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(cabin.passengerDetected==true) SurfacePanel(Modifier.fillMaxWidth()) {
            Text("동승자 감지",color=Mint)
            Text("함께하는 휴식·오디오 세션을 추천합니다.",color=White)
            ActionButton("함께 휴식",onRelax,color=Mint,enabled=s.schedule.availableSeconds>=60)
            if(s.schedule.availableSeconds<60)Text("휴식을 시작하려면 출발 준비 시간을 제외하고 최소 1분이 필요합니다.",color=Amber)
        }
        if(!cabin.readiness.isNullOrEmpty()) SurfacePanel(Modifier.fillMaxWidth()) {
            Text("휴식 전 실내 확인 · 차량 제공 정보",color=Mint)
            cabin.readiness.orEmpty().forEach { Text(it,color=White) }
        }
        if(!cabin.care.isNullOrEmpty()) SurfacePanel(Modifier.fillMaxWidth()) {
            Text("정차 중 차량 점검 · 차량 제공 정보",color=Amber)
            cabin.care.orEmpty().forEach { Text(it,color=White) }
        }
    }
}
