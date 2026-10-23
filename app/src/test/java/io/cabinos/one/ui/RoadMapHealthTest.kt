package io.cabinos.one.ui
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.osmdroid.util.GeoPoint

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class RoadMapHealthTest {
    private var now=0L
    private fun map()=RoadMapView(ApplicationProvider.getApplicationContext(),{now}).apply {
        setUseDataConnection(false)
        measure(android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(500,android.view.View.MeasureSpec.EXACTLY));layout(0,0,800,500)
        controller.setZoom(15.0);controller.setCenter(GeoPoint(37.5547,126.9723))
    }
    private fun fill(map:RoadMapView,indices:List<Long>){indices.forEach {map.tileProvider.tileCache.putTile(it,BitmapDrawable(map.resources,Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)))}}
    @Test fun healthRequiresEveryVisibleBitmapAndTimeoutIsNotHiddenByOneGoodTile(){
        val m=map();try {
            val tiles=m.visibleTileIndices();assertTrue(tiles.size>=4);assertEquals(RoadMapStatus.LOADING,m.sampleHealth().status)
            fill(m,tiles.take(1));assertEquals(RoadMapStatus.PARTIAL,m.sampleHealth().status)
            now=15001;assertEquals(RoadMapStatus.UNAVAILABLE,m.sampleHealth().status)
            fill(m,tiles);assertEquals(RoadMapStatus.READY,m.sampleHealth().status)
        }finally{m.endHealth();m.onDetach()}
    }
    @Test fun oldViewportBitmapsDoNotMakeNewEmptyAreaLookReady(){
        val m=map();try {
            fill(m,m.visibleTileIndices());assertEquals(RoadMapStatus.READY,m.sampleHealth().status)
            m.controller.setCenter(GeoPoint(48.85,2.35));assertEquals(0,m.sampleHealth().loaded)
            now=16000;assertEquals(RoadMapStatus.UNAVAILABLE,m.sampleHealth().status)
            m.retryRoads();assertEquals(RoadMapStatus.LOADING,m.sampleHealth().status)
        }finally{m.endHealth();m.onDetach()}
    }
    @Test fun offlineHealthDistinguishesNoMapFromCachedMap(){
        val m=map();try {
            m.connection(false);assertEquals(RoadMapStatus.OFFLINE,m.sampleHealth().status);assertEquals(0,m.health.loaded)
            fill(m,m.visibleTileIndices());assertEquals(RoadMapStatus.OFFLINE,m.sampleHealth().status);assertTrue(m.health.loaded>0)
        }finally{m.endHealth();m.onDetach()}
    }
}
