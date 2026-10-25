package io.cabinos.one.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import io.cabinos.core.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*

@Composable fun NearbyScreen(s:AppState,vm:CabinViewModel,onLocate:()->Unit,onNavigate:(NavPlace)->Unit={vm.liveNavigation.plan(it)}) {
    val status by vm.nearbyStatus.collectAsStateWithLifecycle()
    val busy by vm.nearbyBusy.collectAsStateWithLifecycle()
    val selected by vm.mapSelection.collectAsStateWithLifecycle()
    val compact=LocalCompactWindow.current
    val context=LocalContext.current
    var map by remember { mutableStateOf<MapView?>(null) }
    val chosen=s.activities.firstOrNull { it.activity.id==selected } ?: s.activities.firstOrNull()
    Column(if(compact)Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("멈춘 시간, 어디에서 보낼까요?",color=White,fontSize=24.sp)
        Text(s.activityOrigin.label,color=Mint,fontSize=12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ActionButton(if(busy)"확인 중…" else "내 위치 사용",onLocate,color=Blue,enabled=!busy&&s.locationAvailable,icon=Icons.Outlined.MyLocation)
            ActionButton("지도 중심 사용",{map?.mapCenter?.let {vm.mapOrigin(MapPosition(it.latitude,it.longitude,"직접 고른 지도 기준점 · 차량 위치 아님"))}},color=Muted,enabled=!busy)
            ActionButton("주변 검색",{vm.searchNearby()},color=Mint,enabled=!busy&&s.networkAvailable&&s.locationAvailable,icon=Icons.Outlined.Search)
            if(busy)ActionButton("검색 취소",{vm.cancelNearbySearch()},color=Amber)
            if(!busy)ActionButton("예시 초기화",{vm.mapExamples()},color=Muted)
        }
        Text(if(!s.networkAvailable) "오프라인 · 새 지도와 장소를 불러올 수 없습니다." else if(!s.locationAvailable) "위치 사용 제한 · 온라인 지도·검색을 중단했습니다." else status,color=if(s.networkAvailable)Muted else Amber,fontSize=11.sp)
        ResponsiveMapPanels(if(compact) Modifier.fillMaxWidth() else Modifier.weight(1f), compact,
            map = { pane ->
            Box(pane.clip(RoundedCornerShape(18.dp)).background(Panel)) {
                OpenStreetMap(s,selected,{vm.mapSelect(it)},{map=it})
            }
            }, details = { pane ->
            Column(pane,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf("전체","카페","식당","산책","쇼핑").forEach { category ->
                        FilterChip(selected=s.preferences.preferredCategory==category,onClick={vm.nearbyCategory(category)},label={Text(category,fontSize=11.sp)})
                    }
                }
                Text("${s.preferences.preferredCategory} ${s.activities.size}곳 · 출발 ${s.schedule.waitingSeconds/60}분 · 준비 ${s.preferences.departureBufferMinutes}분 확보",color=Mint,fontSize=12.sp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    if(s.activities.isEmpty()) Text("이 분류의 검색 결과가 없습니다. 다른 분류 또는 지도 중심을 선택해 주변 검색을 눌러 주세요.",color=Muted,fontSize=13.sp)
                    s.activities.forEach { option ->
                        val a=option.activity
                        SurfacePanel(Modifier.fillMaxWidth().clickable {vm.mapSelect(a.id)},accent=if(a.id==chosen?.activity?.id)Mint else Line) {
                            Text(a.name,color=White,fontSize=14.sp)
                            Text("${a.category} · 도보 약 ${a.oneWaySeconds/60}분 · ${if(a.simulated)"예시" else "OSM 등록"}",color=Muted,fontSize=11.sp)
                            Text(if(option.feasible)"왕복 후 ${option.usableSeconds/60}분 이용 가능" else "왕복·준비 시간을 확보하기 어렵습니다",color=if(option.feasible)Mint else Amber,fontSize=11.sp)
                        }
                    }
                }
                chosen?.let { option ->
                    val a=option.activity
                    SurfacePanel(Modifier.fillMaxWidth(),accent=Blue) {
                        Text(a.name,color=White,fontSize=16.sp)
                        Text("도보·왕복 시간은 직선거리 기반 추정입니다. 횡단·우회·출입 제한은 반영하지 않으니 실제 경로를 확인하세요.",fontSize=10.sp,color=Muted)
                        Spacer(Modifier.height(7.dp))
                        ActionButton(if(s.selectedActivityId==a.id)"왕복 계획에 반영됨" else "이 장소로 계획",{vm.engine.selectActivity(a.id)},Modifier.fillMaxWidth(),Mint,option.feasible)
                        ActionButton("앱에서 길 안내",{
                            if(vm.state.value.parkedAllowed && a.latitude!=null && a.longitude!=null)
                                onNavigate(NavPlace(a.name,NavPoint(a.latitude!!,a.longitude!!)))
                        },Modifier.fillMaxWidth(),Blue,enabled=a.latitude!=null&&a.longitude!=null)
                        Text("자동차 경로로 안내합니다. 위 도보 시간과 다를 수 있습니다.",color=Muted,fontSize=10.sp)
                    }
                }
            }
            })
        Text("지도: © OpenStreetMap contributors · 주변 검색 시 기준점 좌표를 Overpass에 전송합니다. 영업·보행 가능 여부를 확인하세요.",color=Muted,fontSize=10.sp,modifier=Modifier.clickable {if(vm.state.value.parkedAllowed && !openMapUri(context,"https://www.openstreetmap.org/copyright"))vm.mapUnavailable()})
    }
}

@Composable private fun OpenStreetMap(s:AppState,selected:String?,onSelect:(String)->Unit,onReady:(MapView)->Unit) {
    val appearance=LocalAppearance.current;val palette=LocalCabinPalette.current
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val map=remember {
        configureRoadTiles(context)
        RoadMapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK);setMultiTouchControls(true)
            minZoomLevel=1.0;maxZoomLevel=19.0;controller.setZoom(16.0)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        }
    }
    var health by remember {mutableStateOf(RoadMapHealth())}
    val currentSelect by rememberUpdatedState(onSelect)
    var previousOrigin by remember { mutableStateOf<MapPosition?>(null) }
    var previousSelection by remember { mutableStateOf<String?>(null) }
    var signature by remember { mutableStateOf("") }
    DisposableEffect(map,owner) {
        onReady(map);map.onHealth={health=it};map.beginHealth()
        val observer=LifecycleEventObserver { _,event ->if(event==Lifecycle.Event.ON_START){map.onHealth={health=it};map.onResume();map.beginHealth()} else if(event==Lifecycle.Event.ON_STOP){map.onPause();map.endHealth()} }
        owner.lifecycle.addObserver(observer);map.onResume()
        onDispose {owner.lifecycle.removeObserver(observer);map.endHealth();map.onPause();map.onDetach()}
    }
    Box(Modifier.fillMaxSize()) {
    AndroidView(factory={map},modifier=Modifier.fillMaxSize().clipToBounds(),update={ view ->
        applyMapAppearance(view,appearance,palette)
        view.connection(s.networkAvailable&&s.locationAvailable&&s.parkedAllowed)
        if(previousOrigin!=s.activityOrigin) {view.controller.setCenter(GeoPoint(s.activityOrigin.latitude,s.activityOrigin.longitude));previousOrigin=s.activityOrigin}
        val next="${s.nearbyRevision}|$selected|${s.activities.map {it.activity.id to it.feasible}}"
        if(next!=signature) {
            signature=next;view.overlays.clear()
            view.overlays.add(Marker(view).apply {
                position=GeoPoint(s.activityOrigin.latitude,s.activityOrigin.longitude);title=s.activityOrigin.label
                icon=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(AndroidColor.rgb(88,169,255));setStroke(4,AndroidColor.WHITE);setSize(28,28)}
                setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
            })
            s.activities.forEach { option ->val a=option.activity
                if(a.latitude!=null&&a.longitude!=null) view.overlays.add(Marker(view).apply {
                    position=GeoPoint(a.latitude!!,a.longitude!!);title="${a.name}${if(a.simulated)" · 예시" else ""}"
                    icon=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(if(option.feasible)AndroidColor.rgb(45,176,150) else AndroidColor.rgb(204,141,67));setStroke(if(a.id==selected)5 else 2,AndroidColor.WHITE);setSize(36,36)}
                    setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener {_,_->currentSelect(a.id);true}
                })
            }
            view.overlays.add(CopyrightOverlay(context).apply {setTextSize(12);setCopyrightNotice("© OpenStreetMap contributors")})
            view.invalidate()
        }
        if(previousSelection!=selected) {
            previousSelection=selected
            s.activities.firstOrNull {it.activity.id==selected}?.activity?.let {a->if(a.latitude!=null&&a.longitude!=null)view.controller.animateTo(GeoPoint(a.latitude!!,a.longitude!!))}
        }
    })
    if(health.status!=RoadMapStatus.READY) Column(Modifier.align(Alignment.TopCenter).background(Panel).padding(8.dp)) {
        Text(health.message,color=Amber,fontSize=12.sp)
        if(health.status==RoadMapStatus.UNAVAILABLE)TextButton(onClick={map.retryRoads()}){Text("지도 다시 불러오기")}
    }
    }
}

private fun openMapUri(context:android.content.Context,uri:String):Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(uri)));true
} catch(_:android.content.ActivityNotFoundException) {false} catch(_:SecurityException) {false}
