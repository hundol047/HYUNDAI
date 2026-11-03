package io.cabinos.one

import androidx.activity.compose.setContent
import io.cabinos.one.ui.CabinTheme
import io.cabinos.one.ui.NavigationScreen
import io.cabinos.one.platform.NavigationUi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import android.graphics.Bitmap
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.ui.CabinViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApplicationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test @Config(sdk=[35]) fun assistantFocusCommandCanCloseAndAddTaskOnAndroid15() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.state.value.sample!=null
        }
        compose.runOnIdle {vm.selectScenario("주차")}
        Thread.sleep(1600)
        compose.runOnIdle {
            vm.engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=System.currentTimeMillis()))
            vm.waiting(40)
        }
        compose.onNodeWithText("무엇을 도와드릴까요?").performClick()
        compose.onNodeWithText("25분 집중 모드 켜줘").performClick()
        compose.onNodeWithContentDescription("닫기").performClick()
        assertEquals(SessionStatus.RUNNING,vm.state.value.session?.status)
        compose.onNodeWithText("나의 할 일").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("메일 확인").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.tasks.value.any {it.text=="메일 확인"}
        }
        compose.runOnIdle {vm.selectScenario("신호 대기")}
        assertFalse(vm.state.value.parkedAllowed)
        compose.onNodeWithText("나의 할 일").assertDoesNotExist()
    }

    @Test fun activeGuidanceKeepsRerouteConnectionFailureVisible() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        val error="내비게이션 서버에 연결하지 못했습니다. 네트워크·서비스 주소를 확인해 주세요."
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NavigationScreen(AppState(networkAvailable=false),NavigationUi(
                guidance=Guidance(active=true,offRoute=true,status="경로 이탈 · 재탐색 대기"),notice=error),vm.liveNavigation,{})
        }}}
        compose.onNode(hasText("경로 이탈 · 재탐색 대기") and hasAnyAncestor(hasTestTag("expanded-navigation"))).assertIsDisplayed()
        compose.onNode(hasText(error) and hasAnyAncestor(hasTestTag("expanded-navigation"))).assertIsDisplayed()
        compose.onNodeWithText("검색").assertDoesNotExist()
    }

    @Test fun navigationNamesakeShowsActualDistanceAndHidesItWithoutUsableGps() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        val origin=NavFix(NavPoint(37.6868,128.7550),5.0,System.currentTimeMillis(),0.0)
        val place=NavPlace("대관령 옛길",NavPoint(37.5073769,127.1056803),address="서울특별시 송파구 송파대로 458",category="식당",source="OSM / Nominatim")
        val hint=PlaceSearch.locationHint(origin.point,place.point)
        fun show(ready:Boolean) {compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NavigationScreen(AppState(parkedAllowed=true,networkAvailable=false),NavigationUi(places=listOf(place),mapFix=origin,gpsNotice=if(ready)"검사용 합성 GPS · 정확도 5m · 실기기 수신 아님" else "검사용 GPS 신호 끊김",
                gpsConnection=if(ready)io.cabinos.one.platform.GpsConnection.READY else io.cabinos.one.platform.GpsConnection.STALE),vm.liveNavigation,{})
        }}}}
        show(true)
        compose.onNodeWithText("데모 · 차량에서는 기본 내비 사용").assertIsDisplayed()
        compose.onNodeWithText(hint).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("식당 · OSM / Nominatim").assertIsDisplayed()
        capture("navigation-namesake")
        show(false)
        compose.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test fun activityLaunchAndDrivingSafetyAreRendered() {
        compose.onNodeWithText("CabinOS").assertIsDisplayed()
        val vm = ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.state.value.sample != null
        }
        compose.runOnIdle { vm.selectScenario("주행") }
        compose.onNodeWithText("안전한 여정, 함께할게요.").assertIsDisplayed()
        compose.onNodeWithText("데모 · 차량에서는 기본 내비 사용").assertIsDisplayed()
        compose.runOnIdle { vm.selectScenario("신호 대기") }
        compose.onNodeWithText("신호 대기 중").assertIsDisplayed()
        assertFalse(vm.state.value.parkedAllowed)
        compose.runOnIdle { vm.command("25분 집중 모드 켜줘") }
        assertNull(vm.state.value.session)
        compose.runOnIdle { vm.selectScenario("주차") }
        Thread.sleep(1600)
        compose.runOnIdle {
            vm.engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=System.currentTimeMillis()))
            vm.waiting(40)
        }
        compose.onNodeWithText("StopSense  +  CabinOS").assertIsDisplayed()
        capture("parked")
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("당신에게 맞춘 CabinOS").assertIsDisplayed()
        compose.onNodeWithText("웜 샌드").performScrollTo().performClick()
        compose.onNodeWithText("야간 밝기").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("sand",vm.appearance.value.preset)
            assertEquals("night",vm.appearance.value.brightness)
            assertEquals(.25f,compose.activity.window.attributes.screenBrightness,0f)
            assertEquals("sand",io.cabinos.one.platform.RuntimePreferences(compose.activity).appearance.preset)
        }
        compose.onNodeWithText("실내에 맞춘 화면 색상").performScrollTo()
        capture("appearance")
        compose.runOnIdle {vm.appearance(AppearanceSettings())}
        compose.runOnIdle { vm.engine.tick() }
        compose.onNodeWithText("당신에게 맞춘 CabinOS").assertIsDisplayed()
        compose.onNodeWithText("홈").performClick()
        compose.runOnIdle {vm.engine.setConnectivity(network=false)}
        compose.onNodeWithText("지도").performClick()
        compose.onNodeWithText("멈춘 시간, 어디에서 보낼까요?").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(vm.state.value.activities.indexOfFirst {it.activity.id=="cafe"})
        compose.onNodeWithText("숲속 카페").performClick()
        compose.onNodeWithText("이 장소로 계획").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("cafe",vm.state.value.selectedActivityId)
        capture("nearby")
        compose.onNodeWithText("홈").performClick()
        compose.runOnIdle {vm.engine.setConnectivity(network=true)}
        compose.runOnIdle { vm.start(SessionKind.FOCUS,25) }
        compose.onNodeWithText("나의 할 일").performScrollTo().assertIsDisplayed()
        capture("focus")
        compose.runOnIdle { vm.setForeground(false) }
        assertFalse(vm.state.value.parkedAllowed)
        assertEquals(SessionStatus.PAUSED,vm.state.value.session!!.status)
        compose.runOnIdle { vm.setForeground(true) }
        assertFalse(vm.state.value.parkedAllowed)
        Thread.sleep(1600)
        compose.runOnIdle { vm.engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=System.currentTimeMillis())) }
        assertTrue(vm.state.value.parkedAllowed)
        assertEquals(SessionStatus.PAUSED,vm.state.value.session!!.status)
        compose.runOnIdle { vm.engine.pauseOrResume() }
        assertEquals(SessionStatus.RUNNING,vm.state.value.session!!.status)
        compose.runOnIdle { vm.selectScenario("주행") }
        compose.onNodeWithText("안전한 여정, 함께할게요.").assertIsDisplayed()
        assertEquals(SessionStatus.PAUSED,vm.state.value.session!!.status)
        capture("driving")
        compose.runOnIdle { vm.selectScenario("실차 연결") }
        compose.onNodeWithText("차량 상태를 확인할 수 없습니다.").assertIsDisplayed()
        compose.onNodeWithText("판교IC · 데모 경로").assertDoesNotExist()
        assertFalse(vm.state.value.parkedAllowed)
    }
    @Test fun navigationScreenRendersOfflineInternationalRouteAndDrivingControls() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        val points=listOf(NavPoint(48.85,2.35),NavPoint(48.86,2.35),NavPoint(48.87,2.36))
        val route=NavigationGeometry.route(NavPlace("Paris · 검사용 목적지",points.last()),points,3000.0,600.0,listOf(NavManeuver(points[0],"depart"),NavManeuver(points[1],"turn","left","Rue du Louvre"),NavManeuver(points[2],"arrive")))
        compose.runOnIdle {compose.activity.setContent {CabinTheme {NavigationScreen(AppState(parkedAllowed=true,networkAvailable=false),NavigationUi(gpsConnection=io.cabinos.one.platform.GpsConnection.DENIED,gpsNotice="검사용 권한 거부 · 실기기 GPS 수신 아님"),vm.liveNavigation,{})}}}
        compose.onNodeWithText("위치 설정").assertIsDisplayed()
        compose.onNodeWithText("앱 권한 설정").assertIsDisplayed()
        capture("gps-permission")
        compose.runOnIdle {compose.activity.setContent {CabinTheme {NavigationScreen(AppState(parkedAllowed=true,networkAvailable=false),NavigationUi(busy=true,notice="검사용 지연 응답 · 실제 네트워크 검사 아님",gpsNotice="검사용 GPS 수신 대기"),vm.liveNavigation,{})}}}
        compose.onNodeWithText("요청 취소").assertIsDisplayed()
        compose.onNodeWithText("검사용 GPS 수신 대기").assertIsDisplayed()
        capture("navigation-pending")
        val tracker=NavigationTracker();tracker.preview(route);tracker.start(NavFix(points[0],5.0,System.currentTimeMillis(),0.0))
        compose.runOnIdle {compose.activity.setContent {CabinTheme {NavigationScreen(AppState(networkAvailable=false),NavigationUi(tracker.state,notice="검사용 합성 GPS·경로 · 실제 도로 주행 아님",gpsNotice="검사용 합성 GPS · 정확도 5m · 실기기 수신 아님"),vm.liveNavigation,{})}}}
        compose.onNodeWithText("내비게이션").assertIsDisplayed()
        compose.onNode(hasText("좌회전 · Rue du Louvre") and hasAnyAncestor(hasTestTag("expanded-navigation"))).assertIsDisplayed()
        compose.onNode(hasText("안내 종료") and hasAnyAncestor(hasTestTag("expanded-navigation"))).assertIsDisplayed()
        compose.onNodeWithText("다시 듣기").assertIsDisplayed()
        compose.onNodeWithText("안내 시작").assertDoesNotExist()
        capture("navigation")
    }

    private fun capture(name:String) {
        val output=File("build/screenshots/$name.png")
        output.parentFile?.mkdirs()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(image))
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            image.recycle()
        }
    }

    @Test fun realRoomDatabasePreservesCheckpointSettingsAndHistory() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.dao()
            val p=Preferences(focusMinutes=45,departureBufferMinutes=10,voiceEnabled=false)
            val session=CabinSession("checkpoint-session",SessionKind.FOCUS,1000,1500,900,SessionStatus.RUNNING)
            val completed=CabinSession("finished",SessionKind.RELAX,100,600,0,SessionStatus.COMPLETED)
            dao.saveState(AppState(preferences=p,session=session,history=listOf(completed),schedule=SharedSchedule(departureAtMs=500_000)))
            val checkpoint=dao.checkpoint()!!
            assertEquals("checkpoint-session",checkpoint.activeSessionId)
            assertEquals(500_000L,checkpoint.departure)
            val restored=UnifiedVehicleContextEngine({2000L})
            restored.restore(dao.preferences()!!.domain(),dao.session(checkpoint.activeSessionId!!)!!.domain(),dao.history().map{it.domain()},checkpoint.departure)
            assertEquals(45,restored.state.value.preferences.focusMinutes)
            assertEquals(SessionStatus.PAUSED,restored.state.value.session!!.status)
            assertEquals(900L,restored.state.value.session!!.remainingSeconds)
            assertEquals(1,restored.state.value.history.size)
            assertFalse(restored.state.value.parkedAllowed)
        } finally { db.close() }
    }
}
