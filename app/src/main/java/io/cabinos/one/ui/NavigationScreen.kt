package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.cabinos.core.*
import io.cabinos.one.platform.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun NavigationScreen(s:AppState,ui:NavigationUi,nav:LiveNavigation,onLocate:()->Unit) {
    val g=ui.guidance
    var query by remember {mutableStateOf("")}
    var accessConfirmed by remember(g.route){mutableStateOf(false)}
    var settings by remember {mutableStateOf(false)}
    var searchEndpoint by remember {mutableStateOf(nav.service.searchEndpoint)}
    var routeEndpoint by remember {mutableStateOf(nav.service.routeEndpoint)}
    var settingsError by remember {mutableStateOf("")}
    val canPlan=s.parkedAllowed&&!g.active
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("EchoRoad · 글로벌 내비게이션",color=White,fontSize=23.sp)
            if(canPlan)TextButton(onClick={settings=true}){Text("서비스 설정",color=Muted)}
        }
        Text(if(g.active||g.arrived)g.status else ui.notice,color=if(g.offRoute||!g.tracking&&g.active)Amber else Mint,fontSize=12.sp)
        if(canPlan) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query,{query=it.take(180)},Modifier.weight(1f),singleLine=true,label={Text("국가·도시·주소 또는 위도,경도")})
                ActionButton("검색",{nav.search(query)},color=Blue,enabled=!ui.busy&&s.networkAvailable&&query.trim().length>=2)
                ActionButton("내 위치 사용",onLocate,color=Mint,enabled=!ui.busy)
            }
        } else if(!g.active&&!g.arrived)Text("목적지 설정은 안전한 주차 확인 후 가능합니다.",color=Amber,fontSize=13.sp)
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(.62f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Panel)) { NavigationMap(g,s.networkAvailable,canPlan) }
            Column(Modifier.weight(.38f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Mint)
                if(canPlan)ui.places.forEach { place ->
                    SurfacePanel(Modifier.fillMaxWidth().clickable(enabled=!ui.busy){nav.plan(place)},accent=Blue) {
                        Text(place.name,color=White,fontSize=13.sp)
                        Text("자동차 경로 계산 →",color=Blue,fontSize=11.sp)
                    }
                }
                g.route?.let {r->
                    SurfacePanel(Modifier.fillMaxWidth(),accent=Blue) {
                        Text(r.destination.name,color=White,fontSize=15.sp)
                        Spacer(Modifier.height(9.dp))
                        if(g.active) {
                            if(g.tracking&&!g.offRoute)Text(g.turnSymbol,color=Blue,fontSize=48.sp)
                            Text(if(g.tracking&&!g.offRoute)g.nextInstruction else "위치·경로 확인 중",color=White,fontSize=23.sp)
                            if(g.tracking&&!g.offRoute)Text("${navDistance(g.turnMeters)} 앞",color=Blue,fontSize=24.sp)
                        }
                        Text("남은 거리 ${navDistance(g.remainingMeters)}",color=Mint,fontSize=18.sp)
                        Text(if(g.arrived)"차량 경로 도착 · 안내 종료" else if(g.active&&(!g.tracking||g.offRoute))"도착 예상 갱신 중지" else "약 ${kotlin.math.ceil(g.remainingSeconds/60).toInt().coerceAtLeast(1)}분 · ${SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(System.currentTimeMillis()+(g.remainingSeconds*1000).toLong()))} 도착 예상",color=Muted,fontSize=13.sp)
                        if(r.destinationAccessMeters>30)Text("차량 접근 지점에서 목적지까지 직선 약 ${navDistance(r.destinationAccessMeters)} · 주차장·진입로를 확인하세요.",color=Amber,fontSize=12.sp)
                        if(canPlan&&r.destinationAccessMeters>100)Row {Checkbox(accessConfirmed,{accessConfirmed=it});Text("목적지와 차량 접근 지점의 차이를 확인했습니다.",color=White,fontSize=12.sp)}
                        Text("실시간 교통·차선 안내 미제공",color=Muted,fontSize=11.sp)
                        Spacer(Modifier.height(9.dp))
                        if(canPlan)ActionButton("안내 시작",{nav.start(accessConfirmed)},Modifier.fillMaxWidth(),Mint,!ui.busy&&(r.destinationAccessMeters<=100||accessConfirmed))
                        ActionButton("안내 종료·경로 지우기",{nav.stop()},Modifier.fillMaxWidth(),Muted)
                        if(g.active&&!g.tracking)ActionButton("GPS 다시 연결",{nav.locate()},Modifier.fillMaxWidth(),Blue)
                        if(g.offRoute)ActionButton("재탐색 재시도",{nav.retry()},Modifier.fillMaxWidth(),Blue,!ui.busy&&s.networkAvailable)
                    }
                }
                if(g.route==null)Text("Seoul, Paris, New York, 東京 등\n국가 제한 없이 검색합니다.\n\n현재 위치는 정확한 기기 GPS를 사용합니다. 좌표·목적지는 저장하지 않습니다.",color=Muted,fontSize=13.sp)
                Text(ui.notice,color=Muted,fontSize=11.sp)
                if(!s.networkAvailable)Text("오프라인 · 새 지도·검색·재탐색 불가. 받아 둔 경로와 GPS 안내만 유지합니다.",color=Amber,fontSize=12.sp)
            }
        }
        Text("© OpenStreetMap contributors · 검색: Nominatim · 자동차 경로: OSRM · 국가별 도로 데이터와 서버 가용성에 따라 경로가 없을 수 있습니다.",color=Muted,fontSize=10.sp)
    }
    if(settings&&canPlan)AlertDialog(onDismissRequest={settings=false},title={Text("내비게이션 서비스")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("기본 공개 서버는 체험용이며 상용 가용성을 보장하지 않습니다. 운영 시 자체/계약 서버로 교체하세요. 입력한 서버에 검색어·경로 좌표가 전달됩니다.")
        OutlinedTextField(searchEndpoint,{searchEndpoint=it},label={Text("Nominatim HTTPS 기본 주소")},singleLine=true)
        OutlinedTextField(routeEndpoint,{routeEndpoint=it},label={Text("OSRM HTTPS 기본 주소")},singleLine=true)
        Text(settingsError,color=Amber)
    }},confirmButton={TextButton(onClick={runCatching {nav.configure(searchEndpoint,routeEndpoint)}.onSuccess {settings=false}.onFailure {settingsError=it.message ?: "주소를 확인하세요."}}){Text("적용")}},dismissButton={TextButton(onClick={settings=false}){Text("닫기")}})
}
private fun navDistance(m:Double)=if(m>=1000)"${"%.1f".format(Locale.US,m/1000)} km" else "${m.toInt().coerceAtLeast(0)} m"

@Composable private fun NavigationMap(g:Guidance,online:Boolean,interactive:Boolean) {
    val appearance=LocalAppearance.current;val palette=LocalCabinPalette.current
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val map=remember {Configuration.getInstance().apply {
        userAgentValue="CabinOS_ONE/0.4.0 (+https://github.com/hundol047/HYUNDAI)"
        osmdroidBasePath=java.io.File(context.cacheDir,"osm");osmdroidTileCache=java.io.File(osmdroidBasePath,"tiles");tileDownloadThreads=2
    };NavigationMapView(context).apply {setTileSource(TileSourceFactory.MAPNIK);minZoomLevel=2.0;maxZoomLevel=19.0;controller.setZoom(15.0)}}
    var lastRoute by remember {mutableStateOf<DrivingRoute?>(null)}
    var previousPalette by remember {mutableStateOf<CabinPalette?>(null)}
    var previousFix by remember {mutableStateOf<NavFix?>(null)}
    DisposableEffect(map,owner) {
        val observer=LifecycleEventObserver {_,e->if(e==Lifecycle.Event.ON_START)map.onResume() else if(e==Lifecycle.Event.ON_STOP)map.onPause()}
        owner.lifecycle.addObserver(observer);map.onResume()
        onDispose {owner.lifecycle.removeObserver(observer);map.onPause();map.onDetach()}
    }
    AndroidView(factory={map},modifier=Modifier.fillMaxSize(),update={view->
        applyMapAppearance(view,appearance,palette)
        view.setUseDataConnection(online);view.setMultiTouchControls(interactive)
        view.gesturesEnabled=interactive
        view.importantForAccessibility=if(interactive)android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.zoomController.setVisibility(if(interactive)org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT else org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        if(lastRoute!==g.route||previousFix!=g.fix||previousPalette!=palette) {
            view.overlays.clear()
            g.route?.let {r->
                view.overlays.add(Polyline(view).apply {setPoints(r.points.map {GeoPoint(it.latitude,it.longitude)});outlinePaint.color=palette.accent or (0xFF shl 24);outlinePaint.strokeWidth=9f})
                view.overlays.add(Marker(view).apply {position=GeoPoint(r.destination.point.latitude,r.destination.point.longitude);title=r.destination.name})
                if(lastRoute!==r&&!g.active)view.controller.setCenter(GeoPoint(r.points.first().latitude,r.points.first().longitude))
            }
            g.fix?.let {f->
                view.overlays.add(Marker(view).apply {
                    position=GeoPoint(f.point.latitude,f.point.longitude);title=if(g.tracking)"현재 GPS 위치" else "마지막 확인 위치"
                    icon=android.graphics.drawable.GradientDrawable().apply {shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(if(g.tracking)android.graphics.Color.CYAN else android.graphics.Color.GRAY);setStroke(3,android.graphics.Color.WHITE);setSize(30,30)}
                    setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                })
                if(g.active&&g.tracking)view.controller.setCenter(GeoPoint(f.point.latitude,f.point.longitude))
            }
            view.overlays.add(CopyrightOverlay(context).apply {setCopyrightNotice("© OpenStreetMap contributors")})
            lastRoute=g.route;previousFix=g.fix;previousPalette=palette;view.invalidate()
        }
    })
}

/** Guidance text remains accessible outside the read-only map during driving. */
private class NavigationMapView(context:android.content.Context):MapView(context) {
    var gesturesEnabled=true
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean=if(gesturesEnabled)super.dispatchTouchEvent(event) else false
    override fun performClick():Boolean=super.performClick()
}
