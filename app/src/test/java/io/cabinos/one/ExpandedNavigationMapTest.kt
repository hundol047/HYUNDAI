package io.cabinos.one

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.cabinos.core.*
import io.cabinos.one.platform.NavigationUi
import io.cabinos.one.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedNavigationMapTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test @Config(qualifiers="w1024dp-h480dp-land-mdpi")
    fun shortLandscapeWithLargeFontKeepsMapAndExitReachable() {
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.5f)) {
                ExpandableNavigationMap(NavigationUi(
                    guidance=Guidance(active=true,tracking=true,turnMeters=150.0,nextInstruction="오른쪽 도로로 진입하세요"),
                    gpsNotice="기기 위치 · 정확도 약 12m",
                    echoAhead="경로 주변 과거 사고 통계 구역 · 현재 사고 아님",
                    notice="실시간 교통·차선 안내 미제공 · 현장 표지를 확인하세요"
                ),false,false){}
            }
        }}}
        compose.onNodeWithText("지도 축소").assertIsDisplayed()
        compose.onNodeWithText("안내 종료").assertIsDisplayed()
        fun maps(view:android.view.View):List<org.osmdroid.views.MapView> =
            if(view is org.osmdroid.views.MapView)listOf(view)
            else if(view is android.view.ViewGroup)(0 until view.childCount).flatMap {maps(view.getChildAt(it))}
            else emptyList()
        compose.runOnIdle {
            val root=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val map=maps(root).single()
            assertTrue("도로를 읽을 수 있는 지도 높이가 필요합니다: ${map.height}",map.height>=180*map.resources.displayMetrics.density)
        }
    }
    private fun expandAndClose() {
        var picks=0
        val ui=NavigationUi(gpsNotice="GPS 신호 대기 · 회전 안내 일시중지")
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                ExpandableNavigationMap(ui,false,false){picks++}
            }
        }}}
        compose.onNodeWithContentDescription("지도 크게 보기").performTouchInput {click(center)}
        compose.onNodeWithTag("expanded-navigation").assertIsDisplayed().assertHeightIsAtLeast(500.dp)
        compose.onNodeWithText("지도 축소").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(ui.gpsNotice).assertIsDisplayed()
        compose.onNodeWithText("지도 중심을 목적지로").assertDoesNotExist()
        compose.onNodeWithText("지도 축소").performClick()
        compose.onNodeWithTag("expanded-navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("지도 크게 보기").assertIsDisplayed()
        compose.onNodeWithContentDescription("지도 크게 보기").performClick()
        compose.runOnIdle {org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed()}
        compose.onNodeWithTag("expanded-navigation").assertDoesNotExist()
        assertEquals(0,picks)
    }
    @Test fun guidanceStartExpandsOnceAndManualCollapseSurvivesUpdates() {
        val state=androidx.compose.runtime.mutableStateOf(NavigationUi())
        var repeated=0;var stopped=0
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                ExpandableNavigationMap(state.value,false,false,onRepeat={repeated++},onStop={stopped++}){}
            }
        }}}
        compose.onNodeWithTag("expanded-navigation").assertDoesNotExist()
        compose.runOnIdle {state.value=state.value.copy(guidance=Guidance(active=true,tracking=true,nextInstruction="시험 우회전",turnMeters=250.0))}
        compose.onNodeWithTag("expanded-navigation").assertIsDisplayed()
        compose.onNodeWithText("250 m 앞").assertIsDisplayed()
        compose.onNodeWithText("다시 듣기").performClick()
        compose.runOnIdle {assertEquals(1,repeated)}
        compose.onNodeWithText("지도 축소").performClick()
        compose.runOnIdle {state.value=state.value.copy(guidance=state.value.guidance.copy(turnMeters=200.0))}
        compose.onNodeWithTag("expanded-navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("지도 크게 보기").performClick()
        compose.onNodeWithText("안내 종료").performClick()
        compose.runOnIdle {assertEquals(1,stopped)}
        compose.onNodeWithTag("expanded-navigation").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi")
    fun activeLandscapeShowsTurnsAndControls() {
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            ExpandableNavigationMap(NavigationUi(guidance=Guidance(active=true,tracking=true,turnMeters=150.0)),false,false){}
        }}}
        compose.onNodeWithText("150 m 앞").assertIsDisplayed()
        compose.onNodeWithText("다시 듣기").assertIsDisplayed().assertHeightIsAtLeast(52.dp)
        compose.onNodeWithText("안내 종료").assertIsDisplayed().assertHeightIsAtLeast(52.dp)
    }
    @Test fun phoneMapTapExpandsAndClosePreservesNavigation(){expandAndClose()}
    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi")
    fun landscapeMonitorUsesExpandedWindow(){expandAndClose()}
}
