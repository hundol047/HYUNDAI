package io.cabinos.one.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*

internal fun nextArrivalAt(now:Long,hour:Int,minute:Int,zone:ZoneId=ZoneId.systemDefault()):Long {
    val instant=Instant.ofEpochMilli(now)
    val date=instant.atZone(zone).toLocalDate()
    // Choose an actual future occurrence: gaps have no offset, overlaps have two.
    for(day in 0L..2L) {
        val local=date.plusDays(day).atTime(hour,minute)
        val next=zone.rules.getValidOffsets(local).map { local.toInstant(it) }.filter { it.isAfter(instant) }.minOrNull()
        if(next!=null)return next.toEpochMilli()
    }
    error("No valid arrival time within three local dates")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ArrivalClockControls(now:Long,enabled:Boolean,onSet:(Long)->Unit) {
    val initial=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).plusHours(1)
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    if(!enabled)Text("차량 내비에서 목적지를 먼저 설정하세요",color=Amber)
    Text("도착 시각 ${"%02d:%02d".format(hour,minute)} · 다음 가능한 시각으로 설정",color=White)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={hour=(hour+23)%24},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("시 −")}
        OutlinedButton(onClick={hour=(hour+1)%24},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("시 +")}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={minute=(minute+59)%60},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("분 −")}
        OutlinedButton(onClick={minute=(minute+1)%60},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=56.dp)){Text("분 +")}
    }
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),maxItemsInEachRow=3) {
        listOf(30,60,120).forEach { minutes ->
            OutlinedButton(onClick={val target=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).plusMinutes(minutes.toLong());hour=target.hour;minute=target.minute},enabled=enabled,modifier=Modifier.heightIn(min=56.dp)) { Text("${minutes}분 후") }
        }
    }
    Button(onClick={onSet(nextArrivalAt(now,hour,minute))},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("도착 시각 적용") }
}
