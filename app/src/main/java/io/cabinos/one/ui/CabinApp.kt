package io.cabinos.one.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.*
import io.cabinos.core.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun CabinApp(vm:CabinViewModel=hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val vehicleStatus by vm.vehicleStatus.collectAsStateWithLifecycle()
    val storageStatus by vm.storageStatus.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val navigationState by vm.liveNavigation.state.collectAsStateWithLifecycle()
    var navigationOpen by remember { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_START) vm.setForeground(true)
            if(event==Lifecycle.Event.ON_STOP) vm.setForeground(false)
        }
        lifecycle.addObserver(observer)
        vm.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer);vm.setForeground(false) }
    }
    val context=LocalContext.current
    val view=LocalView.current
    DisposableEffect(view,s.parkedAllowed,s.session?.status,navigationState.guidance.active) {
        view.keepScreenOn=navigationState.guidance.active || (s.parkedAllowed && s.session?.status==SessionStatus.RUNNING)
        onDispose { view.keepScreenOn=false }
    }
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "dashboard"
    var assistantOpen by remember { mutableStateOf(false) }
    var simulatorOpen by remember { mutableStateOf(false) }
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) vm.listen() else vm.command("마이크 권한 없이 도움말") }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {vm.refreshNotifications()}
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {vm.requestLocation()}
    val voiceAvailable by vm.speech.voiceAvailable.collectAsStateWithLifecycle()
    val permissionPreferences=remember(context) { io.cabinos.one.platform.RuntimePreferences(context) }
    var permissionAsked by rememberSaveable { mutableStateOf(permissionPreferences.vehiclePermissionRequested) }
    var permissionInFlight by rememberSaveable { mutableStateOf(false) }
    var speedPermissionMissing by remember { mutableStateOf(context.checkSelfPermission("android.car.permission.CAR_SPEED")!=PackageManager.PERMISSION_GRANTED) }
    var permissionsBeforeRequest by rememberSaveable { mutableStateOf<Map<String,Boolean>>(emptyMap()) }
    val vehiclePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionInFlight=false
        speedPermissionMissing=context.checkSelfPermission("android.car.permission.CAR_SPEED")!=PackageManager.PERMISSION_GRANTED
        if(io.cabinos.one.platform.vehiclePermissionChanged(permissionsBeforeRequest,result)) vm.selectScenario("실차 연결")
    }
    val requestVehiclePermissions:(Boolean)->Unit = { manual ->
        val activity=context as? android.app.Activity
        val openSettings=manual && permissionAsked && speedPermissionMissing && activity!=null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity,"android.car.permission.CAR_SPEED")
        if (!permissionInFlight && openSettings) {
            try {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")))
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context,"시스템 설정에서 CabinOS ONE의 차량 읽기 권한을 허용해 주세요.",android.widget.Toast.LENGTH_LONG).show()
            }
        } else if (!permissionInFlight) {
            permissionPreferences.vehiclePermissionRequested=true
            permissionAsked=true;permissionInFlight=true
            val requested=arrayOf("android.car.permission.CAR_SPEED","android.car.permission.CAR_ENERGY")
            permissionsBeforeRequest=requested.associateWith { context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED }
            vehiclePermission.launch(requested)
        }
    }
    val checkVehiclePermissions:()->Unit = {
        speedPermissionMissing=context.checkSelfPermission("android.car.permission.CAR_SPEED")!=PackageManager.PERMISSION_GRANTED
        if(vm.automotive && speedPermissionMissing && !permissionAsked) requestVehiclePermissions(false)
    }
    val currentPermissionCheck by rememberUpdatedState(checkVehiclePermissions)
    DisposableEffect(lifecycle,vm) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_START) currentPermissionCheck()
            // Returning to the app refreshes permission status, never repeats a denied prompt.
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle,vm) {
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) currentPermissionCheck()
    }
    LaunchedEffect(vehicleStatus) {
        speedPermissionMissing=context.checkSelfPermission("android.car.permission.CAR_SPEED")!=PackageManager.PERMISSION_GRANTED
    }
    val navigationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {vm.liveNavigation.locate()}
    val requestNavigationLocation:()->Unit = {
        if(vm.state.value.parkedAllowed) {
            if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)vm.liveNavigation.locate()
            else navigationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
    val nearbyPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {vm.mapLocate()}
    val requestNearbyLocation:()->Unit = {
        if(vm.state.value.parkedAllowed) {
            if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)vm.mapLocate()
            else nearbyPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
    val requestNotifications:()->Unit = { if(android.os.Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.refreshNotifications() }
    LaunchedEffect(s.parkedAllowed) { if(!s.parkedAllowed && route!="dashboard") nav.navigate("dashboard") {popUpTo("dashboard"){inclusive=true}} }
    DisposableEffect(context,appearance.brightness) {
        val window=(context as? android.app.Activity)?.window
        val old=window?.attributes?.screenBrightness
        window?.let {it.attributes=it.attributes.apply {screenBrightness=AppearanceCatalog.windowBrightness(appearance.brightness)}}
        onDispose {if(window!=null&&old!=null)window.attributes=window.attributes.apply {screenBrightness=old}}
    }
    CabinTheme(appearance) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).background(Brush.linearGradient(listOf(Night,Panel,Night)))) {
            val compact=maxWidth<840.dp || maxHeight<520.dp
            CompositionLocalProvider(LocalCompactWindow provides compact) {
                val selected=if(navigationOpen||navigationState.guidance.active) "navigation" else route
                val navigate:(String)->Unit = { if(it=="navigation")navigationOpen=true else {navigationOpen=false;nav.navigate(it){launchSingleTop=true}} }
                Row(Modifier.fillMaxSize()) {
                    if(!compact) NavigationRailPanel(selected,s.parkedAllowed,navigate,{assistantOpen=true},{simulatorOpen=true},vm.automotive,voiceAvailable)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=if(compact)12.dp else 24.dp)) {
                        if(compact) Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("CabinOS ONE",color=White,fontSize=23.sp,modifier=Modifier.weight(1f))
                            TextButton(onClick={simulatorOpen=true}){Text(if(vm.automotive) "차량 상태" else "연결 / 데모")}
                        } else BrandHeader(s)
                        storageStatus?.let { Text(it,color=Amber,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) }
                        Box(Modifier.weight(1f)) {
                            if(vm.automotive && (navigationOpen || route=="nearby")) VehicleNavigationPanel(s,vm)
                            else if(navigationOpen || navigationState.guidance.active) NavigationScreen(s,navigationState,vm.liveNavigation,requestNavigationLocation)
                            else if(vm.automotive && !s.parkedAllowed) VehicleParkedNotice(s,vehicleStatus,speedPermissionMissing,{requestVehiclePermissions(true)})
                            else if(!s.parkedAllowed) DrivingDashboard(s,onVoice={vm.command("앞에 위험 구간 있어?")},onNavigation={navigationOpen=true})
                            else ParkedNavigation(nav,vm,requestNotifications,requestNearbyLocation)
                        }
                        if(compact) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                            listOf("홈" to "dashboard","내비" to "navigation","지도" to "nearby","기록" to "history","설정" to "settings").forEach { (label,key) ->
                                TextButton(onClick={navigate(key)},enabled=s.parkedAllowed||key in listOf("dashboard","navigation"),modifier=Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(2.dp)){Text(label,color=if(selected==key)Mint else Muted)}
                            }
                            TextButton(onClick={assistantOpen=true},modifier=Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(2.dp)){Text(if(voiceAvailable)"음성" else "명령")}
                        } else BottomBar(s,onVoice={assistantOpen=true},onSimulator={simulatorOpen=true},voiceAvailable=voiceAvailable)
                    }
                }
            }
        }
        if(assistantOpen) AssistantDialog(s,vm,onClose={assistantOpen=false},onMicrophone={
            if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED || !s.parkedAllowed) vm.listen()
            else microphone.launch(Manifest.permission.RECORD_AUDIO)
        })
        if(simulatorOpen) SimulatorDialog(s,vm,onClose={simulatorOpen=false},onLocation={locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))},onVehiclePermissions={requestVehiclePermissions(true)})
    }
}
@Composable private fun ParkedNavigation(nav:androidx.navigation.NavHostController,vm:CabinViewModel,onNotifications:()->Unit,onLocate:()->Unit) {
    // Collect live timing inside destinations so a tick never rebuilds the navigation graph.
    NavHost(navController=nav,startDestination="dashboard") {
        composable("dashboard") { val s by vm.state.collectAsStateWithLifecycle();if(LocalCompactWindow.current) CompactParkedDashboard(s,vm,onMap={nav.navigate("nearby"){launchSingleTop=true}}) else ParkedDashboard(s,vm,onMap={nav.navigate("nearby"){launchSingleTop=true}}) }
        composable("nearby") {val s by vm.state.collectAsStateWithLifecycle();NearbyScreen(s,vm,onLocate)}
        composable("history") { val s by vm.state.collectAsStateWithLifecycle();HistoryScreen(s) }
        composable("settings") { val s by vm.state.collectAsStateWithLifecycle();SettingsScreen(s,vm,onNotifications) }
    }
}
@Composable private fun NavigationRailPanel(route:String,parked:Boolean,onNavigate:(String)->Unit,onAssistant:()->Unit,onDemo:()->Unit,automotive:Boolean,voiceAvailable:Boolean) {
    Column(Modifier.width(76.dp).fillMaxHeight().background(Night).border(1.dp,Line.copy(alpha=.4f)).padding(vertical=23.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Icon(Icons.Outlined.Hexagon,"CabinOS ONE",tint=Mint,modifier=Modifier.size(30.dp))
        Spacer(Modifier.height(24.dp))
        RailItem(Icons.Outlined.SpaceDashboard,"홈",route=="dashboard",true){onNavigate("dashboard")}
        RailItem(Icons.Outlined.Navigation,"내비",route=="navigation",true){onNavigate("navigation")}
        RailItem(Icons.Outlined.Map,"지도",route=="nearby",parked){onNavigate("nearby")}
        RailItem(Icons.Outlined.History,"기록",route=="history",parked){onNavigate("history")}
        RailItem(Icons.Outlined.Tune,"설정",route=="settings",parked){onNavigate("settings")}
        RailItem(if(voiceAvailable)Icons.Outlined.MicNone else Icons.Outlined.TouchApp,if(voiceAvailable)"음성" else "명령",false,true,onAssistant)
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Outlined.Science,if(automotive)"차량 상태" else "데모",false,true,onDemo)
        Text("ONE",fontSize=10.sp,color=Muted,letterSpacing=3.sp)
    }
}
@Composable private fun RailItem(icon:ImageVector,label:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    Column(Modifier.width(58.dp).heightIn(min=58.dp).background(if(selected) Mint.copy(alpha=.12f) else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(14.dp)).clickable(enabled=enabled,onClick=onClick).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Icon(icon,label,tint=if(selected) Mint else if(enabled) Muted else Line,modifier=Modifier.size(23.dp))
        Text(label,color=if(selected) Mint else if(enabled) Muted else Line,fontSize=10.sp)
    }
}
@Composable private fun BrandHeader(s:AppState) {
    Row(Modifier.fillMaxWidth().height(86.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("CabinOS",fontSize=27.sp,fontWeight=FontWeight.Light,letterSpacing=(-1).sp,color=White)
        Spacer(Modifier.width(7.dp));Text("ONE",fontSize=27.sp,fontWeight=FontWeight.Bold,color=White)
        Spacer(Modifier.width(20.dp));Box(Modifier.width(1.dp).height(20.dp).background(Line));Spacer(Modifier.width(20.dp))
        Text("이동의 모든 순간을 연결하다",color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
        Icon(Icons.Outlined.Wifi,if(s.networkAvailable)"네트워크 연결" else "오프라인",tint=if(s.networkAvailable)Muted else Amber,modifier=Modifier.size(16.dp))
        Spacer(Modifier.width(14.dp))
        Text(SimpleDateFormat("a h:mm",Locale.KOREA).format(Date(s.nowMs)),fontSize=14.sp,color=White)
    }
}
@Composable private fun BottomBar(s:AppState,onVoice:()->Unit,onSimulator:()->Unit,voiceAvailable:Boolean) {
    Row(Modifier.fillMaxWidth().height(66.dp),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=onSimulator) {Icon(Icons.Outlined.Science,null,Modifier.size(15.dp),tint=Amber);Spacer(Modifier.width(6.dp));Text(if(s.sample?.source==DataSource.VEHICLE)if(s.sample?.connected==true)"차량 속도·기어 연결 · 읽기 전용" else "차량 신호 미확인 · 안전 제한" else "데모 모드 — 시뮬레이션 데이터",fontSize=11.sp,color=Amber)}
        Spacer(Modifier.weight(1f))
        Text(if(!s.locationAvailable)"위치 사용 불가" else if(!s.networkAvailable)"오프라인 · 로컬 실행" else "모든 순간, 하나의 경험",color=Muted,fontSize=11.sp)
        Spacer(Modifier.width(18.dp))
        OutlinedButton(onClick=onVoice,shape=RoundedCornerShape(50),border=BorderStroke(1.dp,Blue.copy(alpha=.45f)),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)) {
            Icon(if(voiceAvailable)Icons.Outlined.MicNone else Icons.Outlined.TouchApp,null,Modifier.size(18.dp),tint=Blue);Spacer(Modifier.width(8.dp));Text("무엇을 도와드릴까요?",fontSize=12.sp,color=White)
        }
    }
}

@Composable private fun VehicleParkedNotice(s:AppState,status:String,permissionMissing:Boolean,onPermission:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("안전한 정차를 기다립니다",color=White,fontSize=26.sp)
        SurfacePanel(Modifier.fillMaxWidth(),accent=Amber) {
            Text(if(s.sample?.connected==true) "P 기어와 완전한 정지를 확인해 주세요." else "차량 속도·기어 신호를 확인하고 있습니다.",color=White,fontSize=20.sp)
            Spacer(Modifier.height(12.dp))
            Text(status,color=Amber,fontSize=14.sp)
            if(permissionMissing) Button(onClick=onPermission,modifier=Modifier.heightIn(min=48.dp)) {Text("차량 읽기 권한 허용")}
            Text("주행 중에는 차량 기본 내비게이션을 사용하세요. CabinOS의 집중·휴식·설정은 안전한 주차 확인 후 열립니다.",color=Muted,fontSize=14.sp)
        }
    }
}
