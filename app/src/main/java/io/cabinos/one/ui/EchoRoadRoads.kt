package io.cabinos.one.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cabinos.core.*

/** Ordinary-device demo only. Automotive driving remains blocked by VehicleParkedNotice. */
@Composable internal fun EchoRoadRoads(s:AppState,fix:NavFix?,modifier:Modifier=Modifier) {
    val place=if(fix==null)NavPlace("판교역 · 데모 위치 (GPS 아님)",NavPoint(37.3948,127.1112))
        else NavPlace("마지막 GPS 확인 위치 · 현재 위치와 다를 수 있음",fix.point)
    Column(modifier.background(Panel)) {
        Text(place.name,color=Amber,fontSize=12.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NavigationMap(Guidance(),s.networkAvailable,true,place,null,false,{},showPicker=false,showHealth=true,placeZoom=17.0)
        }
        Text("건물·가게 표기는 지도 제공 데이터 기준 · 확대하면 더 자세히 표시",color=Muted,fontSize=10.sp,modifier=Modifier.padding(6.dp))
    }
}
