package io.cabinos.one.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import io.cabinos.core.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.advancedpolyline.MonochromaticPaintList

/** Keep the road geometry alive across GPS updates; only the position marker moves. */
internal class NavigationMapRenderer(private val map: MapView) {
    private var traffic: io.cabinos.one.platform.TrafficFlow? = null
    private var trafficLine: Polyline? = null

    fun traffic(value: io.cabinos.one.platform.TrafficFlow?) {
        if (traffic == value) return
        traffic = value
        trafficLine?.let { map.overlays.remove(it) }
        trafficLine = null
        value?.let { f ->
            trafficLine =
                Polyline(map).apply {
                    setPoints(f.points.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = f.color
                    outlinePaint.strokeWidth = 13f
                }
            map.overlays.add(trafficLine)
        }
        map.invalidate()
    }

    private var safetyPoints: List<RoadSafetyPoint> = emptyList()
    private val safetyMarkers = mutableListOf<Marker>()

    fun safety(points: List<RoadSafetyPoint>) {
        if (safetyPoints == points) return
        safetyPoints = points
        safetyMarkers.forEach { map.overlays.remove(it) }
        safetyMarkers.clear()
        points.forEach { p ->
            val marker =
                Marker(map).apply {
                    position = GeoPoint(p.point.latitude, p.point.longitude)
                    title = if (p.kind == RoadSafetyKind.BUMP) "과속방지턱 · 참고 위치" else "단속카메라 · 방향 미확인"
                    subDescription = "공공데이터 · ${p.road} · 자료 ${p.recordDate} · 현장 표지 확인"
                    icon =
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 8f
                            setColor(Color.rgb(255, 190, 70))
                            setStroke(2, Color.BLACK)
                            setSize(24, 24)
                        }
                }
            safetyMarkers += marker
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    private var echoRisks:List<EchoRisk> = emptyList()
    private val echoOverlays=mutableListOf<Overlay>()
    fun echo(risks:List<EchoRisk>) {
        if(echoRisks==risks)return
        echoRisks=risks
        echoOverlays.forEach {map.overlays.remove(it)};echoOverlays.clear()
        for(r in risks) {
            r.area?.polygons?.forEach {p ->
                val area=Polygon(map).apply {
                    points=p.first().map {GeoPoint(it.latitude,it.longitude)}
                    holes=p.drop(1).map {ring -> ring.map {GeoPoint(it.latitude,it.longitude)}}
                    fillPaint.color=Color.argb(30,255,185,60)
                    outlinePaint.color=Color.rgb(224,150,45);outlinePaint.strokeWidth=3f
                    title=r.label;subDescription="한국도로교통공단 · 과거 사고 통계 · 현재 사고·결빙 상태 아님"
                }
                echoOverlays+=area;map.overlays.add(0,area)
            }
            val marker=Marker(map).apply {
                position=GeoPoint(r.point.latitude,r.point.longitude);title=r.label
                subDescription=if(r.area!=null)"공공 통계 참고 구역 · 현재 사고·결빙 아님" else "기기 제보 · 사고 확정 아님"
                icon=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(Color.rgb(245,180,70));setStroke(2,Color.BLACK);setSize(22,22)}
            }
            echoOverlays+=marker;map.overlays.add(marker)
        }
        map.invalidate()
    }

    private var route: DrivingRoute? = null
    private var selected: NavPlace? = null
    private var line: Polyline? = null
    private var routePaint: Paint? = null
    private var routeBorder: Paint? = null
    private var destination: Marker? = null
    private var position: Marker? = null
    private var point: NavPoint? = null
    private var tracking: Boolean? = null
    var followPosition = true
    private var following = false
    private var accent: Int? = null
    private fun routeEdge(color:Int)=if(AppearanceCatalog.contrast(color,Color.WHITE)>AppearanceCatalog.contrast(color,Color.BLACK))Color.WHITE else Color.rgb(15,32,45)

    init {
        map.overlays.add(
            CopyrightOverlay(map.context).apply {
                setCopyrightNotice("© OpenStreetMap contributors")
            }
        )
    }

    fun update(
        g: Guidance,
        color: Int,
        selectedPlace: NavPlace? = null,
        location: NavFix? = g.fix,
        locationCurrent: Boolean = g.tracking,
        placeZoom: Double = 16.0,
    ) {
        var changed = false
        if (route !== g.route) {
            line?.let { map.overlays.remove(it) }
            destination?.let { map.overlays.remove(it) }
            line = null
            routePaint = null
            routeBorder = null
            destination = null
            route = g.route
            route?.let { r ->
                line =
                    Polyline(map).apply {
                        setPoints(r.points.map { GeoPoint(it.latitude, it.longitude) })
                        val density=map.resources.displayMetrics.density
                        val stroke=outlinePaint.apply {
                            this.color=color;strokeWidth=6f*density;strokeCap=Paint.Cap.ROUND
                        }
                        val border=Paint(stroke).apply {this.color=routeEdge(color);strokeWidth=10f*density}
                        routePaint=stroke;routeBorder=border
                        // Two paints share one geometry; light/custom accents remain distinct from land.
                        outlinePaintLists.add(MonochromaticPaintList(border))
                        outlinePaintLists.add(MonochromaticPaintList(stroke))
                    }
                // Roads stay behind destination and GPS markers.
                map.overlays.add(0, line)
                destination =
                    Marker(map).apply {
                        position =
                            GeoPoint(r.destination.point.latitude, r.destination.point.longitude)
                        title = r.destination.name
                    }
                map.overlays.add(destination)
                if (!g.active)
                    map.controller.setCenter(
                        GeoPoint(r.points.first().latitude, r.points.first().longitude)
                    )
            }
            accent = color
            changed = true
        }
        if (g.route == null && selected != selectedPlace) {
            destination?.let { map.overlays.remove(it) }
            destination = null
            selected = selectedPlace
            selectedPlace?.let { p ->
                destination =
                    Marker(map).apply {
                        position = GeoPoint(p.point.latitude, p.point.longitude)
                        title = p.name
                        subDescription = p.address
                    }
                map.overlays.add(destination)
                map.controller.setZoom(placeZoom)
                map.controller.setCenter(GeoPoint(p.point.latitude, p.point.longitude))
            }
            changed = true
        }
        if (g.route != null) selected = null
        if (accent != color) {
            routePaint?.color = color
            routeBorder?.color = routeEdge(color)
            // osmdroid's outlinePaint getter switches back to a single paint.
            line?.outlinePaintLists
            accent = color
            changed = true
        }
        val fix = location
        if (fix != null) {
            val firstPosition = point == null
            val moved = point != fix.point
            if (position == null) {
                position =
                    Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
                map.overlays.add(position)
                changed = true
            }
            if (moved) {
                position!!.position = GeoPoint(fix.point.latitude, fix.point.longitude)
                point = fix.point
                changed = true
            }
            if (tracking != locationCurrent) {
                tracking = locationCurrent
                position!!.title = if (locationCurrent) "현재 GPS 위치" else "마지막 확인 위치"
                position!!.icon =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (locationCurrent) Color.CYAN else Color.GRAY)
                        setStroke(3, Color.WHITE)
                        setSize(30, 30)
                    }
                changed = true
            }
            val startingFollow = g.active && g.tracking && !following
            if (startingFollow || (firstPosition && g.route == null && selectedPlace == null))
                map.controller.setZoom(map.zoomLevelDouble.coerceAtLeast(15.0))
            if (
                (followPosition && g.active && g.tracking && (moved || !following)) ||
                    (firstPosition && g.route == null && selectedPlace == null)
            )
                map.controller.setCenter(GeoPoint(fix.point.latitude, fix.point.longitude))
        } else if (position != null) {
            map.overlays.remove(position)
            position = null
            point = null
            tracking = null
            changed = true
        }
        following = g.active && g.tracking
        if (changed) map.invalidate()
    }
}
