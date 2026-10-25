package io.cabinos.one.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clipToBounds
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
    val compact=LocalCompactWindow.current
    val context=LocalContext.current
    DisposableEffect(nav){onDispose {nav.cancelVoice()}}
    val g=ui.guidance
    var query by remember {mutableStateOf("")}
    var nearby by remember {mutableStateOf(false)}
    var submittedQuery by remember {mutableStateOf("")}
    var submittedNearby by remember {mutableStateOf(false)}
    LaunchedEffect(ui.searchQuery) {
        if(ui.searchQuery.isNotBlank()){query=ui.searchQuery;submittedQuery=ui.searchQuery;submittedNearby=nearby}
    }
    var accessConfirmed by remember(g.route){mutableStateOf(false)}
    var languageDialog by remember {mutableStateOf(false)}
    var languageTag by remember(ui.language){mutableStateOf(ui.language)}
    val microphone=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){granted->if(granted)nav.listenDestination() else nav.voiceError("마이크 권한이 거부됐습니다. 주차 후 앱 권한 설정에서 허용해 주세요.")}
    var settings by remember {mutableStateOf(false)}
    var searchEndpoint by remember {mutableStateOf(nav.service.searchEndpoint)}
    var routeEndpoint by remember {mutableStateOf(nav.service.routeEndpoint)}
    var settingsError by remember {mutableStateOf("")}
    var accessToken by remember {mutableStateOf("")}
    val voiceAvailable by nav.voiceAvailable.collectAsStateWithLifecycle()
    val canPlan=s.parkedAllowed&&!g.active
    val chooseDestination:(NavPlace)->Unit = { place ->
        nav.navigateTo(place)
        if(nav.state.value.gpsConnection==GpsConnection.DENIED)onLocate()
    }
    LaunchedEffect(ui.gpsConnection,ui.voiceNotice) {
        if(ui.gpsConnection==GpsConnection.DENIED && ui.voiceNotice.startsWith("인식:"))onLocate()
    }
    Column(if(compact)Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("EchoRoad · 글로벌 내비게이션",color=White,fontSize=23.sp)
            if(canPlan)Row {TextButton(onClick={languageDialog=true}){Text("언어 · ${ui.language}",color=Mint)};TextButton(onClick={settings=true}){Text("서비스 설정",color=Muted)}}
        }
        Text("데모 · 차량에서는 기본 내비 사용",color=Amber,fontSize=12.sp)
        Text(if(g.active||g.arrived)g.status else ui.notice,color=if(g.offRoute||g.directionUncertain||!g.tracking&&g.active)Amber else Mint,fontSize=12.sp)
        Text(ui.gpsNotice+if(ui.gpsAgeSeconds!=null)" · ${ui.gpsAgeSeconds}초 전 수신" else "",color=if(ui.gpsConnection in listOf(GpsConnection.POOR,GpsConnection.STALE,GpsConnection.DENIED,GpsConnection.DISABLED))Amber else Muted,fontSize=11.sp)
        if(canPlan&&ui.gpsConnection in listOf(GpsConnection.POOR,GpsConnection.STALE,GpsConnection.DENIED,GpsConnection.DISABLED))Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(onClick={nav.locate()}){Text("GPS 다시 연결",color=Blue)}
            TextButton(onClick={nav.openLocationSettings()}){Text("위치 설정",color=Blue)}
            TextButton(onClick={nav.openLocationSettings(true)}){Text("앱 권한 설정",color=Blue)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(voiceAvailable)ActionButton(if(ui.voiceListening)"음성 입력 취소" else "🎙 음성 길찾기",{
                if(ui.voiceListening)nav.cancelVoice()
                else if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)nav.listenDestination()
                else if(canPlan)microphone.launch(android.Manifest.permission.RECORD_AUDIO)
                else nav.voiceError("주행 중에는 새 마이크 권한 창을 열지 않습니다. 주차 후 권한을 허용해 주세요.")
            },color=if(ui.voiceListening)Amber else Blue)
            Text(ui.voiceNotice,color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
        }
        if(!voiceAvailable)Text("음성 서비스 사용 불가 · 목적지 검색과 안내 버튼을 사용하세요",color=Muted,fontSize=12.sp)
        if(canPlan&&voiceAvailable)Text("음성: 목적지 → 1번~6번 선택 시 안내 / 주행 중: 안내 반복·안내 종료 · start navigation / repeat / stop navigation",color=Muted,fontSize=10.sp)
        if(canPlan) {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query,{if(ui.busy)nav.cancelPending();query=it.take(180)},if(compact)Modifier.width(240.dp) else Modifier.widthIn(min=260.dp,max=500.dp),singleLine=true,label={Text("가게명·지점·도시·주소 또는 위도,경도")},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={if(!ui.busy){submittedQuery=query;submittedNearby=nearby;nav.search(query,nearby)}}))
                ActionButton("검색",{submittedQuery=query;submittedNearby=nearby;nav.search(query,nearby)},color=Blue,enabled=!ui.busy&&s.networkAvailable&&query.trim().length>=2)
                ActionButton("내 위치 사용",onLocate,color=Mint,enabled=!ui.busy)
                if(ui.busy)ActionButton("요청 취소",{nav.cancelPending()},color=Amber)
            }
            Row {Checkbox(nearby,{nearby=it},enabled=!ui.busy);Text("내 위치 주변 20km에서 찾기 · 결과에 없는 가게가 있을 수 있습니다.",color=Muted,fontSize=11.sp)}
        } else if(!g.active&&!g.arrived)Text("목적지 설정은 안전한 주차 확인 후 가능합니다.",color=Amber,fontSize=13.sp)
        ResponsiveMapPanels(if(compact) Modifier.fillMaxWidth() else Modifier.weight(1f), compact,
            map = { pane ->
            Box(pane.clip(RoundedCornerShape(18.dp)).background(Panel)) { NavigationMap(g,s.networkAvailable,canPlan,ui.selectedPlace,ui.mapFix,!ui.busy,{chooseDestination(it)}) }
            }, details = { pane ->
            Column(pane.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(ui.busy){LinearProgressIndicator(Modifier.fillMaxWidth(),color=Mint);Text("요청 중 · 지도 확대/축소는 계속 사용할 수 있습니다.",color=Muted,fontSize=11.sp)}
                if(canPlan)ui.places.forEachIndexed { index,place ->
                    SurfacePanel(Modifier.fillMaxWidth().clickable(enabled=!ui.busy){chooseDestination(place)},accent=Blue) {
                        Text("${index+1}. ${place.name}",color=White,fontSize=15.sp)
                        if(place.address.isNotBlank())Text(place.address,color=Muted,fontSize=11.sp)
                        Text(listOf(place.category,place.source).filter {it.isNotBlank()}.joinToString(" · "),color=Muted,fontSize=10.sp)
                        val locationHint=PlaceSearch.locationHint(ui.mapFix?.takeIf {ui.gpsConnection==GpsConnection.READY}?.point,place.point)
                        if(locationHint.isNotEmpty())Text(locationHint,color=Amber,fontSize=11.sp)
                        if(place.phone.isNotBlank())Text("전화 ${place.phone}",color=Muted,fontSize=11.sp)
                        if(place.openingHours.isNotBlank())Text("등록 영업시간 ${place.openingHours} · 현재 영업 여부 미확인",color=Muted,fontSize=10.sp)
                        if(place.website.isNotBlank())TextButton(onClick={nav.openPlaceWebsite(place)}){Text("상세정보/웹사이트 ↗",fontSize=11.sp,color=Blue)}
                        Text("내 위치에서 이곳으로 안내 시작 →",color=Blue,fontSize=11.sp)
                    }
                }
                if(canPlan&&ui.hasMore&&submittedQuery==query&&submittedNearby==nearby)ActionButton("다른 결과 더 보기 (최대 18개)",{nav.search(query,nearby,true)},Modifier.fillMaxWidth(),Blue,!ui.busy)
                if(canPlan&&ui.places.isEmpty()) {
                    Text("가게명에 지점·도시를 함께 입력해 보세요. 현지어와 영문 검색 결과가 다를 수 있습니다.",color=Muted,fontSize=11.sp)
                    if(RegionalPlaceSearch.splits(query).isNotEmpty())ActionButton("지역을 나눠 주변 후보 찾기",{nav.searchRegional(query)},Modifier.fillMaxWidth(),Blue,!ui.busy&&s.networkAvailable)
                }
                if(g.route==null)ui.selectedPlace?.let {p->
                    SurfacePanel(Modifier.fillMaxWidth(),accent=Blue) {
                        Text(p.name,color=White,fontSize=15.sp)
                        if(p.address.isNotBlank())Text(p.address,color=Muted,fontSize=11.sp)
                        Text(p.source,color=Muted,fontSize=11.sp)
                        Text(ui.notice,color=Amber,fontSize=12.sp)
                        Text(ui.gpsNotice,color=Muted,fontSize=12.sp)
                        if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                        ActionButton("현재 위치에서 안내 시작",{chooseDestination(p)},Modifier.fillMaxWidth(),Mint,!ui.busy)
                    }
                }
                g.route?.let {r->
                    SurfacePanel(Modifier.fillMaxWidth(),accent=Blue) {
                        Text(r.destination.name,color=White,fontSize=15.sp)
                        Text(r.destination.source,color=Muted,fontSize=11.sp)
                        if(r.destination.address.isNotBlank())Text(r.destination.address,color=Muted,fontSize=11.sp)
                        Spacer(Modifier.height(9.dp))
                        if(g.active) {
                            if(g.tracking&&!g.offRoute&&!g.directionUncertain)Text(g.turnSymbol,color=Blue,fontSize=48.sp)
                            Text(if(g.tracking&&!g.offRoute)g.nextInstruction else "위치·경로 확인 중",color=White,fontSize=23.sp)
                            if(g.tracking&&!g.offRoute&&!g.directionUncertain)Text("${navDistance(g.turnMeters)} 앞",color=Blue,fontSize=24.sp)
                        }
                        if(!g.arrived)Text("남은 거리 ${if(g.directionUncertain)"약 " else ""}${navDistance(g.remainingMeters)}",color=Mint,fontSize=18.sp)
                        Text(if(g.arrived)"차량 경로 도착 · 안내 종료" else if(g.active&&(!g.tracking||g.offRoute||g.directionUncertain))"도착 예상 갱신 중지" else "약 ${kotlin.math.ceil(g.remainingSeconds/60).toInt().coerceAtLeast(1)}분 · ${SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(System.currentTimeMillis()+(g.remainingSeconds*1000).toLong()))} 도착 예상",color=Muted,fontSize=13.sp)
                        if(r.destinationAccessMeters>30)Text("차량 접근 지점에서 목적지까지 직선 약 ${navDistance(r.destinationAccessMeters)} · 주차장·진입로를 확인하세요.",color=Amber,fontSize=12.sp)
                        if(canPlan&&r.destinationAccessMeters>100)Row {Checkbox(accessConfirmed,{accessConfirmed=it});Text("목적지와 차량 접근 지점의 차이를 확인했습니다.",color=White,fontSize=12.sp)}
                        Text("실시간 교통·차선 안내 미제공",color=Muted,fontSize=11.sp)
                        Spacer(Modifier.height(9.dp))
                        if(canPlan&&!ui.originReady)Text(if(ui.originAccessMeters>45)"출발 GPS와 연결도로 시작점이 약 ${navDistance(ui.originAccessMeters)} 떨어져 있습니다. 도로 근처에서 정확한 위치를 확인하세요. 이 사이 진입도로는 안내하지 않습니다." else "출발 위치의 정확하고 최신인 GPS를 확인해 주세요.",color=Amber,fontSize=11.sp)
                        if(canPlan)ActionButton("안내 시작",{nav.start(accessConfirmed)},Modifier.fillMaxWidth(),Mint,!ui.busy&&ui.originReady&&(r.destinationAccessMeters<=100||accessConfirmed))
                        if(canPlan)ActionButton("현재 위치로 경로 다시 계산",{nav.plan(r.destination)},Modifier.fillMaxWidth(),Blue,!ui.busy&&s.networkAvailable)
                        ActionButton("안내 종료·경로 지우기",{nav.stop()},Modifier.fillMaxWidth(),Muted)
                        if(g.active&&!g.tracking)ActionButton("GPS 다시 연결",{nav.locate()},Modifier.fillMaxWidth(),Blue)
                        if(g.offRoute)ActionButton("재탐색 재시도",{nav.retry()},Modifier.fillMaxWidth(),Blue,!ui.busy&&s.networkAvailable)
                    }
                }
                if(canPlan&&g.route==null&&ui.selectedPlace!=null)TextButton(onClick={nav.clearSelection()}){Text("선택 지우기",color=Blue)}
                if(g.route==null)Text("Seoul, Paris, New York, 東京 등\n국가 제한 없이 검색합니다.\n\n현재 위치는 정확한 기기 GPS를 사용합니다. 좌표·목적지는 저장하지 않습니다.",color=Muted,fontSize=13.sp)
                Text(ui.notice,color=Muted,fontSize=11.sp)
                if(!s.networkAvailable)Text("오프라인 · 새 지도·검색·재탐색 불가. 받아 둔 경로와 GPS 안내만 유지합니다.",color=Amber,fontSize=12.sp)
            }
            })
        Text("© OpenStreetMap contributors · 검색: Nominatim · 자동차 경로: OSRM · 국가별 도로 데이터와 서버 가용성에 따라 경로가 없을 수 있습니다.",color=Muted,fontSize=10.sp)
    }
    if(languageDialog&&canPlan)AlertDialog(onDismissRequest={languageDialog=false},title={Text("길 안내·목적지 음성 언어")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("31개 지역별 길 안내 번역을 내장했습니다. 다른 언어는 영어 안내로 대체합니다. 음성 인식·재생은 기기 서비스와 언어팩에 따릅니다.",fontSize=12.sp)
        OutlinedTextField(languageTag,{languageTag=it.take(48)},label={Text("언어 코드 · ko-KR / en-US / fr-FR")},singleLine=true)
        val codes=remember { (NavigationLanguages.codes+nav.availableVoiceLanguages()).distinct().sorted() }
        Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState())){codes.forEach {tag->TextButton(onClick={languageTag=tag}){Text("${Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(tag))} · $tag")}}}
        Text("기기 TTS 보고 언어 ${nav.availableVoiceLanguages().size}개 · 인식 언어는 실제 요청 결과로 확인합니다.",fontSize=11.sp)
    }},confirmButton={TextButton(enabled=NavigationLanguages.validTag(languageTag),onClick={nav.setLanguage(languageTag);languageDialog=false}){Text("적용")}},dismissButton={TextButton(onClick={languageDialog=false}){Text("닫기")}})
    if(settings&&canPlan)AlertDialog(onDismissRequest={accessToken="";settings=false},title={Text("내비게이션 서비스")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("기본 공개 서버는 체험용이며 상용 가용성을 보장하지 않습니다. 운영 시 자체/계약 서버로 교체하세요. 입력한 서버에 검색어·경로 좌표가 전달됩니다.")
        OutlinedTextField(searchEndpoint,{searchEndpoint=it},label={Text("주소·가게 검색 HTTPS 기본 주소")},singleLine=true)
        OutlinedTextField(routeEndpoint,{routeEndpoint=it},label={Text("OSRM HTTPS 기본 주소")},singleLine=true)
        OutlinedTextField(accessToken,{accessToken=it.take(256)},label={Text("자체 검색 서버 접근키 · 저장하지 않음")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("카카오 등 데이터 API 키는 서버에만 보관합니다. 이 값은 자체 서버 접근용으로 설정에 저장하지 않으며 개인정보를 삭제하면 지웁니다.",fontSize=11.sp)
        Text(settingsError,color=Amber)
    }},confirmButton={TextButton(onClick={runCatching {nav.configure(searchEndpoint,routeEndpoint,accessToken)}.onSuccess {accessToken="";settings=false}.onFailure {settingsError=it.message ?: "주소를 확인하세요."}}){Text("적용")}},dismissButton={TextButton(onClick={accessToken="";settings=false}){Text("닫기")}})
}
private fun navDistance(m:Double)=if(m>=1000)"${"%.1f".format(Locale.US,m/1000)} km" else "${m.toInt().coerceAtLeast(0)} m"

@Composable internal fun NavigationMap(g:Guidance,online:Boolean,interactive:Boolean,selectedPlace:NavPlace?,location:NavFix?,pickEnabled:Boolean,onPick:(NavPlace)->Unit,showPicker:Boolean=true,showHealth:Boolean=false,placeZoom:Double=16.0) {
    val appearance=LocalAppearance.current;val palette=LocalCabinPalette.current
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val map=remember {configureRoadTiles(context);RoadMapView(context)}
    val renderer=remember(map){NavigationMapRenderer(map)}
    var health by remember {mutableStateOf(RoadMapHealth())}
    DisposableEffect(map,owner) {
        map.onHealth={health=it}
        val observer=LifecycleEventObserver {_,e->if(e==Lifecycle.Event.ON_START){map.onHealth={health=it};map.onResume();map.beginHealth()}else if(e==Lifecycle.Event.ON_STOP){map.onPause();map.endHealth()}}
        owner.lifecycle.addObserver(observer);map.onResume();map.beginHealth()
        onDispose {owner.lifecycle.removeObserver(observer);map.endHealth();map.onPause();map.onDetach()}
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory={map},modifier=Modifier.fillMaxSize().clipToBounds(),update={view->
            view.onHealth={health=it};applyMapAppearance(view,appearance,palette)
            view.connection(online);view.setMultiTouchControls(interactive);view.gesturesEnabled=interactive
            view.importantForAccessibility=if(interactive)android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            view.zoomController.setVisibility(if(interactive)org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT else org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            renderer.update(g,palette.accent or (0xFF shl 24),selectedPlace,location ?: g.fix,location!=null||g.tracking,placeZoom)
        })
        if(interactive&&showPicker) {
            Text("+",color=White,fontSize=32.sp,modifier=Modifier.align(androidx.compose.ui.Alignment.Center))
            ActionButton("지도 중심을 목적지로",{val c=map.mapCenter;PlaceSearch.mapPoint(c.latitude,c.longitude)?.let(onPick)},Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp),Blue,pickEnabled)
        }
        if(showHealth||health.status!=RoadMapStatus.READY)Column(Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(Panel.copy(alpha=.96f)).padding(10.dp)) {
            Text(health.message,color=if(health.status in listOf(RoadMapStatus.UNAVAILABLE,RoadMapStatus.OFFLINE))Amber else Muted,fontSize=12.sp)
            if(health.status==RoadMapStatus.UNAVAILABLE&&interactive)TextButton(onClick={map.retryRoads()}){Text("도로 지도 다시 불러오기",color=Blue)}
        }
    }
}
