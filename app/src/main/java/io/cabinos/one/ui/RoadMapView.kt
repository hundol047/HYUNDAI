package io.cabinos.one.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.*

internal enum class RoadMapStatus { LOADING, READY, PARTIAL, UNAVAILABLE, OFFLINE }
internal data class RoadMapHealth(val status:RoadMapStatus=RoadMapStatus.LOADING,val visible:Int=0,val loaded:Int=0) {
    val message get()=when(status){
        RoadMapStatus.LOADING->"도로 지도 불러오는 중…"
        RoadMapStatus.READY->"도로 지도 표시 중"
        RoadMapStatus.PARTIAL->"도로 지도 일부 표시 · 나머지 타일을 기다리는 중"
        RoadMapStatus.UNAVAILABLE->"도로 지도를 불러오지 못했습니다. 네트워크를 확인하고 다시 시도하세요."
        RoadMapStatus.OFFLINE->if(loaded>0)"오프라인 · 저장된 도로 지도 표시" else "오프라인 · 이 지역의 저장된 도로 지도가 없습니다."
    }
}
internal fun configureRoadTiles(context:Context) {Configuration.getInstance().apply {
    userAgentValue="CabinOS_ONE/${io.cabinos.one.BuildConfig.VERSION_NAME} (FutureLabs; Android)"
    osmdroidBasePath=java.io.File(context.cacheDir,"osm");osmdroidTileCache=java.io.File(osmdroidBasePath,"tiles");tileDownloadThreads=2
}}
/** Health is based on the visible cached bitmap tiles, not GPS or a successful route request. */
internal class RoadMapView(context:Context,private val clock:()->Long=android.os.SystemClock::elapsedRealtime):MapView(context) {
    var gesturesEnabled=true
    var onHealth:((RoadMapHealth)->Unit)?=null
    var health=RoadMapHealth();private set
    private var online=true
    private var waitingSince:Long?=null
    private var observing=false
    private val check=object:Runnable {override fun run(){if(!observing)return;sampleHealth();postDelayed(this,1000)}}
    init {setTileSource(TileSourceFactory.MAPNIK);minZoomLevel=1.0;maxZoomLevel=19.0;controller.setZoom(2.0);controller.setCenter(GeoPoint(20.0,0.0))}
    fun connection(available:Boolean){if(online!=available){online=available;waitingSince=null};setUseDataConnection(available);sampleHealth()}
    fun beginHealth(){if(!observing){observing=true;post(check)}}
    fun endHealth(){observing=false;removeCallbacks(check);onHealth=null}
    fun retryRoads(){if(!online)return;waitingSince=null;health=RoadMapHealth();onHealth?.invoke(health);invalidate()}
    internal fun visibleTileIndices():List<Long> {
        if(width<=0||height<=0)return emptyList()
        val p=projection;val s=p.screenRect
        val a=p.toMercatorPixels(s.left,s.top,null);val b=p.toMercatorPixels(s.right-1,s.bottom-1,null)
        val z=floor(zoomLevelDouble).toInt().coerceIn(1,19);val count=1 shl z
        val x0=p.getTileFromMercator(min(a.x,b.x));val x1=p.getTileFromMercator(max(a.x,b.x))
        val y0=p.getTileFromMercator(min(a.y,b.y)).coerceAtLeast(0);val y1=p.getTileFromMercator(max(a.y,b.y)).coerceAtMost(count-1)
        // Only inspect the viewport. No prefetch/download is initiated here.
        if(x1-x0>64||y1-y0>64)return emptyList()
        return (x0..x1).flatMap {x->(y0..y1).map {y->MapTileIndex.getTileIndex(z,((x%count)+count)%count,y)}}.distinct()
    }
    internal fun sampleHealth():RoadMapHealth {
        val indices=visibleTileIndices()
        val loaded=indices.count {index->(tileProvider.tileCache.getMapTile(index) as? BitmapDrawable)?.bitmap?.let {!it.isRecycled}==true}
        val complete=indices.isNotEmpty()&&loaded==indices.size
        // Moving to another unloaded viewport is still the same unresolved gap.
        if(!online||indices.isEmpty()||complete)waitingSince=null else if(waitingSince==null)waitingSince=clock()
        val status=when{!online->RoadMapStatus.OFFLINE;complete->RoadMapStatus.READY;waitingSince?.let {clock()-it>=15_000}==true->RoadMapStatus.UNAVAILABLE;loaded>0->RoadMapStatus.PARTIAL;else->RoadMapStatus.LOADING}
        val next=RoadMapHealth(status,indices.size,loaded)
        if(health!=next){health=next;post {onHealth?.invoke(health)}}
        return next
    }
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);sampleHealth()}
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean=if(gesturesEnabled)super.dispatchTouchEvent(event) else false
    override fun performClick():Boolean=super.performClick()
}
