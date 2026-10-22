package io.cabinos.one.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import io.cabinos.core.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*

/** Keep the road geometry alive across GPS updates; only the position marker moves. */
internal class NavigationMapRenderer(private val map:MapView) {
    private var route:DrivingRoute?=null
    private var line:Polyline?=null
    private var destination:Marker?=null
    private var position:Marker?=null
    private var point:NavPoint?=null
    private var tracking:Boolean?=null
    private var following=false
    private var accent:Int?=null
    init {map.overlays.add(CopyrightOverlay(map.context).apply {setCopyrightNotice("© OpenStreetMap contributors")})}

    fun update(g:Guidance,color:Int) {
        var changed=false
        if(route!==g.route) {
            line?.let {map.overlays.remove(it)};destination?.let {map.overlays.remove(it)}
            line=null;destination=null;route=g.route
            route?.let {r->
                line=Polyline(map).apply {setPoints(r.points.map {GeoPoint(it.latitude,it.longitude)});outlinePaint.color=color;outlinePaint.strokeWidth=9f}
                // Roads stay behind destination and GPS markers.
                map.overlays.add(0,line)
                destination=Marker(map).apply {position=GeoPoint(r.destination.point.latitude,r.destination.point.longitude);title=r.destination.name}
                map.overlays.add(destination)
                if(!g.active)map.controller.setCenter(GeoPoint(r.points.first().latitude,r.points.first().longitude))
            }
            accent=color;changed=true
        }
        if(accent!=color){line?.outlinePaint?.color=color;accent=color;changed=true}
        val fix=g.fix
        if(fix!=null) {
            val firstPosition=point==null
            val moved=point!=fix.point
            if(position==null){position=Marker(map).apply {setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)};map.overlays.add(position);changed=true}
            if(moved){position!!.position=GeoPoint(fix.point.latitude,fix.point.longitude);point=fix.point;changed=true}
            if(tracking!=g.tracking) {
                tracking=g.tracking;position!!.title=if(g.tracking)"현재 GPS 위치" else "마지막 확인 위치"
                position!!.icon=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(if(g.tracking)Color.CYAN else Color.GRAY);setStroke(3,Color.WHITE);setSize(30,30)}
                changed=true
            }
            val startingFollow=g.active&&g.tracking&&!following
            if(startingFollow||(firstPosition&&g.route==null))map.controller.setZoom(map.zoomLevelDouble.coerceAtLeast(15.0))
            if((g.active&&g.tracking&&(moved||!following))||(firstPosition&&g.route==null))map.controller.setCenter(GeoPoint(fix.point.latitude,fix.point.longitude))
        }else if(position!=null){map.overlays.remove(position);position=null;point=null;tracking=null;changed=true}
        following=g.active&&g.tracking
        if(changed)map.invalidate()
    }
}
