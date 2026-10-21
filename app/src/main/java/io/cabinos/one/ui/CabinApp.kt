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
    DisposableEffect(view,s.parkedAllowed,s.session?.status) {
        view.keepScreenOn=s.parkedAllowed && s.session?.status==SessionStatus.RUNNING
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
    val requestNotifications:()->Unit = { if(android.os.Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.refreshNotifications() }
    LaunchedEffect(s.parkedAllowed) { if(!s.parkedAllowed && route!="dashboard") nav.navigate("dashboard") {popUpTo("dashboard"){inclusive=true}} }
    CabinTheme {
        Row(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Night,androidx.compose.ui.graphics.Color(0xFF0B1720),Night)))) {
            NavigationRailPanel(route,s.parkedAllowed,onNavigate={ nav.navigate(it){launchSingleTop=true} },onAssistant={assistantOpen=true},onDemo={simulatorOpen=true})
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=24.dp)) {
                BrandHeader(s)
                Box(Modifier.weight(1f)) {
                    if(!s.parkedAllowed) DrivingDashboard(s,onVoice={vm.command("앞에 위험 구간 있어?")},onNavigation={vm.navigation();assistantOpen=true})
                    else ParkedNavigation(nav,vm,requestNotifications)
                }
                BottomBar(s,onVoice={assistantOpen=true},onSimulator={simulatorOpen=true})
            }
        }
        if(assistantOpen) AssistantDialog(s,vm,onClose={assistantOpen=false},onMicrophone={
            if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED || !s.parkedAllowed) vm.listen()
            else microphone.launch(Manifest.permission.RECORD_AUDIO)
        })
        if(simulatorOpen) SimulatorDialog(s,vm,onClose={simulatorOpen=false},onLocation={locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))})
    }
}
@Composable private fun ParkedNavigation(nav:androidx.navigation.NavHostController,vm:CabinViewModel,onNotifications:()->Unit) {
    // Collect live timing inside destinations so a tick never rebuilds the navigation graph.
    NavHost(navController=nav,startDestination="dashboard") {
        composable("dashboard") { val s by vm.state.collectAsStateWithLifecycle();ParkedDashboard(s,vm) }
        composable("history") { val s by vm.state.collectAsStateWithLifecycle();HistoryScreen(s) }
        composable("settings") { val s by vm.state.collectAsStateWithLifecycle();SettingsScreen(s,vm,onNotifications) }
    }
}
@Composable private fun NavigationRailPanel(route:String,parked:Boolean,onNavigate:(String)->Unit,onAssistant:()->Unit,onDemo:()->Unit) {
    Column(Modifier.width(76.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF091119)).border(1.dp,Line.copy(alpha=.4f)).padding(vertical=23.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Icon(Icons.Outlined.Hexagon,"CabinOS ONE",tint=Mint,modifier=Modifier.size(30.dp))
        Spacer(Modifier.height(24.dp))
        RailItem(Icons.Outlined.SpaceDashboard,"홈",route=="dashboard",true){onNavigate("dashboard")}
        RailItem(Icons.Outlined.History,"기록",route=="history",parked){onNavigate("history")}
        RailItem(Icons.Outlined.Tune,"설정",route=="settings",parked){onNavigate("settings")}
        RailItem(Icons.Outlined.MicNone,"음성",false,true,onAssistant)
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Outlined.Science,"데모",false,true,onDemo)
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
@Composable private fun BottomBar(s:AppState,onVoice:()->Unit,onSimulator:()->Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=onSimulator) {Icon(Icons.Outlined.Science,null,Modifier.size(15.dp),tint=Amber);Spacer(Modifier.width(6.dp));Text(if(s.sample?.source==DataSource.VEHICLE)"실차 연결 미지원 · 안전 제한" else "데모 모드 — 시뮬레이션 데이터",fontSize=11.sp,color=Amber)}
        Spacer(Modifier.weight(1f))
        Text(if(!s.locationAvailable)"위치 사용 불가" else if(!s.networkAvailable)"오프라인 · 로컬 실행" else "모든 순간, 하나의 경험",color=Muted,fontSize=11.sp)
        Spacer(Modifier.width(18.dp))
        OutlinedButton(onClick=onVoice,shape=RoundedCornerShape(50),border=BorderStroke(1.dp,Blue.copy(alpha=.45f)),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)) {
            Icon(Icons.Outlined.MicNone,null,Modifier.size(18.dp),tint=Blue);Spacer(Modifier.width(8.dp));Text("무엇을 도와드릴까요?",fontSize=12.sp,color=White)
        }
    }
}
