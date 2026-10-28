package io.cabinos.one

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.cabinos.core.*
import io.cabinos.one.platform.*
import io.cabinos.one.ui.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Opt-in only: ./gradlew -PliveRoadTiles :app:testDebugUnitTest --tests io.cabinos.one.RoadMapLiveTest */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoadMapLiveTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun find(v:View):RoadMapView? {if(v is RoadMapView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it};return null}
    @Test fun actualOsmdroidDownloadsVisibleRoadTilesAndComposeRendersThem() {
        val service=NavigationService()
        val route=runBlocking {service.route(NavPoint(37.5547,126.9723),NavPlace("서울시청 · 실제 OSRM 경로",NavPoint(37.5663,126.9779)))}
        val fix=NavFix(route.points.first(),5.0,System.currentTimeMillis(),0.0)
        val tracker=NavigationTracker();tracker.preview(route);assertTrue(tracker.start(fix))
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.runOnIdle {compose.activity.setContent {CabinTheme {NavigationScreen(AppState(networkAvailable=true),NavigationUi(guidance=tracker.state,mapFix=fix,gpsConnection=GpsConnection.READY,gpsNotice="실제 OSM 지도·OSRM 경로 · GPS는 공개 장소 시험 좌표 · 실차 주행 아님"),vm.liveNavigation,{})}}}
        var map:RoadMapView?=null
        var mapRoot:View?=null
        val out=File("build/live-map").apply {mkdirs()}
        val render=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888)
        try {compose.waitUntil(35_000){var ready=false;compose.runOnIdle {mapRoot=(shadowOf(compose.activity.windowManager) as ShadowWindowManagerImpl).views.asReversed().firstOrNull {find(it)!=null}
            map=mapRoot?.let(::find);mapRoot?.draw(android.graphics.Canvas(render));ready=map?.sampleHealth()?.status==RoadMapStatus.READY};ready}}
        catch(e:Throwable){
            File(out,"failure.json").writeText(JSONObject().put("health",map?.health.toString()).put("width",map?.width).put("height",map?.height).put("center",map?.mapCenter.toString()).put("zoom",map?.zoomLevelDouble).put("indices",map?.visibleTileIndices().toString()).toString(2))
            File(out,"navigation-roads-failed.png").outputStream().use {render.compress(Bitmap.CompressFormat.PNG,100,it)}
            throw e
        }finally{render.recycle()}
        compose.runOnIdle {
            val m=map!!;val h=m.sampleHealth();assertTrue(h.loaded>=4);assertEquals(h.visible,h.loaded)
            val root=requireNotNull(mapRoot)
            assertNotSame("Guidance should render the automatically expanded map window",compose.activity.window.decorView,root)
            val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(image))
            File(out,"navigation-roads.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            File(out,"results.json").writeText(JSONObject().put("result","passed").put("type","actual Android Compose + osmdroid via Robolectric native graphics, live OSM/OSRM HTTP; no supplied bitmap fixtures").put("visibleTiles",h.visible).put("loadedTiles",h.loaded).put("routePoints",route.points.size).put("physicalDeviceGps",false).put("realRoadDriving",false).toString(2))
        }
    }
    @Test fun echoRoadShowsActualPangyoRoadAndBuildingTiles() {
        compose.runOnIdle {compose.activity.setContent {CabinTheme {DrivingDashboard(AppState(context=VehicleContext.DRIVING,networkAvailable=true),{},{})}}}
        val bitmap=Bitmap.createBitmap(1280,800,Bitmap.Config.ARGB_8888)
        try {
            compose.waitUntil(35_000) {
                var ready=false
                compose.runOnIdle {
                    compose.activity.window.decorView.draw(android.graphics.Canvas(bitmap))
                    ready=find(compose.activity.window.decorView)?.sampleHealth()?.status==RoadMapStatus.READY
                }
                ready
            }
            compose.runOnIdle {
                val map=find(compose.activity.window.decorView)!!
                assertEquals(17.0,map.zoomLevelDouble,0.01)
                assertTrue(map.health.loaded>=4)
                val out=File("build/live-map").apply {mkdirs()}
                compose.activity.window.decorView.draw(android.graphics.Canvas(bitmap))
                File(out,"echoroad-roads.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                File(out,"echoroad-results.json").writeText(JSONObject().put("result","passed").put("visibleTiles",map.health.visible).put("loadedTiles",map.health.loaded).put("zoom",map.zoomLevelDouble).put("gps","public demo coordinate, not GPS").toString(2))
            }
        } finally {bitmap.recycle()}
    }

}
