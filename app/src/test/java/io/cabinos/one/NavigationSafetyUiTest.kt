package io.cabinos.one

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.cabinos.core.*
import io.cabinos.one.platform.NavigationUi
import io.cabinos.one.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationSafetyUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun serviceDialogAndUnsubmittedKeyAreClearedBySafetyLock() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        val parked=mutableStateOf(true)
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NavigationScreen(AppState(parkedAllowed=parked.value,networkAvailable=false),NavigationUi(),vm.liveNavigation,{})
        }}}
        compose.onNodeWithText("서비스 설정").performClick()
        compose.onNodeWithText("자체 검색 서버 접근키 · 저장하지 않음").performTextInput("test-only-unsubmitted-token")
        compose.runOnIdle {parked.value=false}
        compose.onNodeWithText("내비게이션 서비스").assertDoesNotExist()
        compose.runOnIdle {parked.value=true}
        compose.onNodeWithText("내비게이션 서비스").assertDoesNotExist()
        compose.onNodeWithText("서비스 설정").performClick()
        compose.onNodeWithText("test-only-unsubmitted-token").assertDoesNotExist()
    }
    @Test fun languageAndTrafficDialogsStayDismissedAfterSafetyLock() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        val parked=mutableStateOf(true)
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NavigationScreen(AppState(parkedAllowed=parked.value,networkAvailable=false),NavigationUi(),vm.liveNavigation,{})
        }}}
        for((button,title) in listOf("언어 · ko-KR" to "길 안내·목적지 음성 언어","교통 연결" to "교통 중계 서버")) {
            compose.onNodeWithText(button).performClick()
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.runOnIdle {parked.value=false}
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.runOnIdle {parked.value=true}
            compose.onNodeWithText(title).assertDoesNotExist()
        }
    }
    @Test @Config(qualifiers="w640dp-h360dp-land-mdpi")
    fun shortVehicleWindowCanScrollToTheLastServiceSetting() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.4f)) {
                NavigationScreen(AppState(parkedAllowed=true,networkAvailable=false),NavigationUi(),vm.liveNavigation,{})
            }
        }}}
        compose.onNodeWithText("서비스 설정").performScrollTo().performClick()
        compose.onNodeWithTag("navigation-service-fields").performTouchInput {swipeUp()}
        compose.onNodeWithText("자체 검색 서버 접근키 · 저장하지 않음").assertIsDisplayed().performTextInput("test-only-token")
        compose.onNodeWithText("닫기").assertIsDisplayed().performClick()
        compose.onNodeWithText("내비게이션 서비스").assertDoesNotExist()
    }
    @Test fun shortWideNavigationDoesNotCollapseTheMapAndResultsToAStrip() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            androidx.compose.runtime.CompositionLocalProvider(LocalCompactWindow provides false) {
                androidx.compose.foundation.layout.Box(Modifier.height(560.dp)) {
                    NavigationScreen(AppState(parkedAllowed=true,networkAvailable=false),NavigationUi(places=listOf(NavPlace("검사 목적지",NavPoint(37.5,127.0)))),vm.liveNavigation,{})
                }
            }
        }}}
        fun maps(v:android.view.View):List<org.osmdroid.views.MapView> = when(v) {
            is org.osmdroid.views.MapView -> listOf(v)
            is android.view.ViewGroup -> (0 until v.childCount).flatMap {maps(v.getChildAt(it))}
            else -> emptyList()
        }
        compose.runOnIdle {org.junit.Assert.assertTrue("지도 높이가 너무 작습니다",maps(compose.activity.window.decorView).single().height>=250)}
        compose.onNodeWithText("1. 검사 목적지").performScrollTo().assertIsDisplayed()
    }
    @Test fun ambiguousDirectionHidesTheTurnInTheCollapsedRouteCardToo() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        val a=NavPoint(37.5,127.0);val b=NavPoint(37.51,127.01)
        val route=NavigationGeometry.route(NavPlace("목적지",b),listOf(a,b),1500.0,180.0,listOf(NavManeuver(a,"depart"),NavManeuver(b,"arrive")))
        val guidance=Guidance(route=route,active=true,tracking=true,directionUncertain=true,nextInstruction="시험용 우회전 지시",turnMeters=100.0,status="진행 방향 확인 중")
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NavigationScreen(AppState(networkAvailable=false),NavigationUi(guidance=guidance),vm.liveNavigation,{})
        }}}
        compose.onNodeWithText("지도 축소").performClick()
        compose.onNodeWithText("시험용 우회전 지시").assertDoesNotExist()
        compose.onNodeWithText("안내 다시 듣기").assertIsNotEnabled()
    }
}
