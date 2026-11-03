package io.cabinos.one

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.cabinos.core.*
import io.cabinos.one.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoRoadMapTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun find(v:View):RoadMapView? {
        if(v is RoadMapView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it}
        return null
    }
    @Test fun drivingUsesRoadTilesInsteadOfIllustrationAndInventedTurn() {
        compose.runOnIdle {compose.activity.setContent {CabinTheme {DrivingDashboard(AppState(context=VehicleContext.DRIVING,networkAvailable=false),{},{})}}}
        compose.runOnIdle {assertNotNull("EchoRoad must render real road tiles",find(compose.activity.window.decorView))}
        compose.onNodeWithText("판교IC · 데모 경로").assertDoesNotExist()
        compose.onNodeWithText("판교역 · 데모 위치 (GPS 아님)").assertIsDisplayed()
        compose.onNodeWithText("오프라인 · 이 지역의 저장된 도로 지도가 없습니다.").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w360dp-h800dp-port-mdpi")
    fun phoneMapKeepsRoadDetailAndSupportsZoom() {
        compose.runOnIdle {compose.activity.setContent {CabinTheme {DrivingDashboard(AppState(context=VehicleContext.DRIVING,networkAvailable=false),{},{})}}}
        compose.runOnIdle {
            val map=find(compose.activity.window.decorView)!!
            assertEquals(17.0,map.zoomLevelDouble,0.01)
            map.controller.setZoom(18.0)
            assertEquals(18.0,map.zoomLevelDouble,0.01)
            map.controller.setZoom(16.0)
            assertEquals(16.0,map.zoomLevelDouble,0.01)
        }
    }
    @Test fun lastGpsFixIsNotPresentedAsCurrentOrReplacedByDemo() {
        val fix=NavFix(NavPoint(37.5663,126.9779),10.0,1L)
        compose.runOnIdle {compose.activity.setContent {CabinTheme {DrivingDashboard(AppState(context=VehicleContext.DRIVING,networkAvailable=false),{},{},fix)}}}
        compose.onNodeWithText("마지막 GPS 확인 위치 · 현재 위치와 다를 수 있음").assertIsDisplayed()
        compose.onNodeWithText("판교역 · 데모 위치 (GPS 아님)").assertDoesNotExist()
        compose.runOnIdle {assertEquals(fix.point.latitude,find(compose.activity.window.decorView)!!.mapCenter.latitude,0.0001)}
    }

}
