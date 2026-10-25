package io.cabinos.one.ui

import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NavigationMapRendererTest {
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun dateLinePinsAndRoadStayNearTheViewportInsteadOfCrossingTheWholeWorld() {
        for(direction in listOf(1.0,-1.0)) {
            val map=map();val renderer=NavigationMapRenderer(map)
            val a=NavPoint(-16.8,179.999*direction);val b=NavPoint(-16.8,-179.999*direction)
            val r=NavigationGeometry.route(NavPlace("Boundary fixture",b),listOf(a,b),213.0,30.0,listOf(NavManeuver(a,"depart"),NavManeuver(b,"arrive")))
            try {
                renderer.update(Guidance(route=r,fix=NavFix(a,5.0,100),tracking=true),android.graphics.Color.MAGENTA)
                val bitmap=android.graphics.Bitmap.createBitmap(1000,640,android.graphics.Bitmap.Config.ARGB_8888)
                map.draw(android.graphics.Canvas(bitmap))
                for(marker in map.overlays.filterIsInstance<Marker>()) {
                    val point=map.projection.toPixels(marker.position,null)
                    assertTrue("Marker must be near the visible date-line road: $point",point.x in 0..1000&&point.y in 0..640)
                }
                val pixels=IntArray(1000*640);bitmap.getPixels(pixels,0,1000,0,0,1000,640)
                val xs=pixels.indices.filter {(pixels[it] and 0x00ffffff)==0x00ff00ff}.map {it%1000}
                assertTrue("Road must actually be drawn",xs.isNotEmpty())
                assertTrue("A 213m road must not stretch across the screen",xs.max()-xs.min()<200)
                bitmap.recycle()
            }finally{map.onDetach()}
        }
    }
    @Test fun selectedShopWithoutGpsHasAPinButNoInventedRoad() {
        val map=map();val renderer=NavigationMapRenderer(map);val shop=NavPlace("카페 판교점",NavPoint(37.3948,127.1112),id="101",address="검사용 지점 주소")
        try {
            renderer.update(Guidance(),-1,shop);assertEquals(1,map.overlays.filterIsInstance<Marker>().size);assertTrue(map.overlays.none {it is Polyline})
            assertEquals(0.0,GeoPoint(shop.point.latitude,shop.point.longitude).distanceToAsDouble(map.mapCenter),5.0)
            val pin=map.overlays.filterIsInstance<Marker>().single();map.controller.setZoom(8.0);renderer.update(Guidance(),0xFF55AAFF.toInt(),shop)
            assertSame(pin,map.overlays.filterIsInstance<Marker>().single());assertEquals(8.0,map.zoomLevelDouble,0.0)
        }finally{map.onDetach()}
    }
    @Test fun selectingAnotherStoreAtTheSameBuildingUpdatesThePinLabel() {
        val map=map();val renderer=NavigationMapRenderer(map);val a=NavPlace("Store A",NavPoint(37.5,127.0),id="1");val b=a.copy(name="Store B",id="2")
        try {
            renderer.update(Guidance(),-1,a);renderer.update(Guidance(),-1,b)
            assertEquals("Store B",map.overlays.filterIsInstance<Marker>().single().title);assertTrue(map.overlays.none {it is Polyline})
        }finally{map.onDetach()}
    }
    @Test fun freshGpsBeforePlanningCentersOnLocalStreetsWithoutInventingARoute() {
        val map=map();val renderer=NavigationMapRenderer(map);val p=NavPoint(37.5547,126.9723)
        try {
            map.controller.setZoom(2.0)
            renderer.update(Guidance(),-1,location=NavFix(p,5.0,100),locationCurrent=true)
            assertEquals(15.0,map.zoomLevelDouble,0.0);assertEquals(0.0,GeoPoint(p.latitude,p.longitude).distanceToAsDouble(map.mapCenter),5.0)
            assertEquals("현재 GPS 위치",map.overlays.filterIsInstance<Marker>().single().title)
            assertTrue(map.overlays.none {it is Polyline})
        }finally{map.onDetach()}
    }
    @Test fun manualPanSurvivesActiveGpsUpdates() {
        val map=map();val renderer=NavigationMapRenderer(map);val r=route()
        try {
            renderer.update(Guidance(route=r,active=true,tracking=true,fix=NavFix(r.points[0],5.0,100)),-1)
            renderer.followPosition=false
            map.controller.setCenter(GeoPoint(37.7,127.2));val center=GeoPoint(map.mapCenter)
            renderer.update(Guidance(route=r,active=true,tracking=true,fix=NavFix(r.points[10],5.0,200)),-1)
            assertEquals(0.0,center.distanceToAsDouble(map.mapCenter),1.0)
            renderer.followPosition=true
            renderer.update(Guidance(route=r,active=true,tracking=true,fix=NavFix(r.points[20],5.0,300)),-1)
            assertTrue(center.distanceToAsDouble(map.mapCenter)>100)
        }finally{map.onDetach()}
    }
    @Test fun trafficOverlayUsesItsOwnColorAndCanBeRemoved() {
        val map=map();val renderer=NavigationMapRenderer(map)
        try {
            val flow=io.cabinos.one.platform.TrafficFlow(listOf(NavPoint(52.3,4.9),NavPoint(52.31,4.91)),10.0,50.0,1.0,false,1000)
            renderer.traffic(flow)
            assertEquals(0xFFE53935.toInt(),map.overlays.filterIsInstance<Polyline>().single().outlinePaint.color)
            renderer.traffic(null)
            assertTrue(map.overlays.none {it is Polyline})
        }finally{map.onDetach()}
    }
    private fun route(name:String="dense route"):DrivingRoute {
        val points=(0..50_000).map {NavPoint(37.5547,126.9723+it*.000002)}
        return NavigationGeometry.route(NavPlace(name,points.last()),points,9000.0,900.0,listOf(NavManeuver(points.first(),"depart"),NavManeuver(points.last(),"arrive")))
    }
    private fun map()=MapView(ApplicationProvider.getApplicationContext()).apply {
        setUseDataConnection(false)
        measure(android.view.View.MeasureSpec.makeMeasureSpec(1000,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(640,android.view.View.MeasureSpec.EXACTLY))
        layout(0,0,1000,640)
        controller.setZoom(15.0)
    }
    @Test fun firstLocationCentersTheMapAndStartingGuidanceRestoresStreetScale() {
        val map=map();val renderer=NavigationMapRenderer(map);val r=route();val fix=NavFix(r.points.first(),5.0,100)
        try {
            // At zoom 15 a screen pixel is several metres; allow pixel rounding.
            renderer.update(Guidance(fix=fix),-1);assertEquals(0.0,GeoPoint(fix.point.latitude,fix.point.longitude).distanceToAsDouble(map.mapCenter),5.0)
            map.controller.setZoom(2.0);renderer.update(Guidance(route=r,fix=fix),-1);assertEquals(2.0,map.zoomLevelDouble,0.0)
            renderer.update(Guidance(route=r,fix=fix,active=true,tracking=true),-1);assertEquals(15.0,map.zoomLevelDouble,0.0)
        }finally{map.onDetach()}
    }
    @Test fun frequentGpsUpdatesAndThemeChangesKeepTheSameRoadGeometryAndMarkers() {
        val map=map();val renderer=NavigationMapRenderer(map);val r=route()
        fun guidance(i:Int)=Guidance(route=r,active=true,tracking=true,fix=NavFix(r.points[i],5.0,i.toLong()))
        try {
            renderer.update(guidance(0),0xFF55AAFF.toInt());val line=map.overlays.filterIsInstance<Polyline>().single();val markers=map.overlays.filterIsInstance<Marker>().toList()
            repeat(100){renderer.update(guidance(it+1),0xFF55AAFF.toInt())}
            assertSame(line,map.overlays.filterIsInstance<Polyline>().single());assertEquals(50_001,line.actualPoints.size)
            assertEquals(2,map.overlays.filterIsInstance<Marker>().size);assertTrue(map.overlays.containsAll(markers))
            renderer.update(guidance(101),0xFFDBC29B.toInt());assertSame(line,map.overlays.filterIsInstance<Polyline>().single());assertEquals(0xFFDBC29B.toInt(),line.outlinePaint.color)
        }finally{map.onDetach()}
    }
    @Test fun previewUpdatesPreserveUserZoomAndPanAndGpsLossChangesMarkerState() {
        val map=map();val renderer=NavigationMapRenderer(map);val r=route();val fix=NavFix(r.points.first(),5.0,100)
        try {
            renderer.update(Guidance(route=r,fix=fix,tracking=true),-1);map.controller.setZoom(9.0);map.controller.setCenter(GeoPoint(-33.8688,151.2093));val before=GeoPoint(map.mapCenter)
            renderer.update(Guidance(route=r,fix=fix.copy(timestampMs=200),tracking=true),-1)
            assertEquals(9.0,map.zoomLevelDouble,0.0);assertEquals(0.0,before.distanceToAsDouble(map.mapCenter),1.0)
            val gps=map.overlays.filterIsInstance<Marker>().first {it.title=="현재 GPS 위치"};val icon=gps.icon
            renderer.update(Guidance(route=r,fix=fix,tracking=false),-1);assertSame(gps,map.overlays.filterIsInstance<Marker>().first {it.title=="마지막 확인 위치"});assertNotSame(icon,gps.icon)
        }finally{map.onDetach()}
    }
    @Test fun routeReplacementAndClearRemoveObsoleteRoadsWithoutLeakingOverlays() {
        val map=map();val renderer=NavigationMapRenderer(map)
        try {
            renderer.update(Guidance(route=route()),-1);val first=map.overlays.filterIsInstance<Polyline>().single()
            renderer.update(Guidance(route=route("replacement")),-1);assertNotSame(first,map.overlays.filterIsInstance<Polyline>().single());assertEquals(1,map.overlays.filterIsInstance<Marker>().size)
            renderer.update(Guidance(),-1);assertTrue(map.overlays.none {it is Polyline||it is Marker});assertEquals(1,map.overlays.filterIsInstance<CopyrightOverlay>().size)
        }finally{map.onDetach()}
    }
}
