package io.cabinos.core

import kotlin.math.*

/** WGS84 navigation coordinates, independent of the map renderer's Mercator limits. */
data class NavPoint(val latitude:Double,val longitude:Double) {
    fun valid()=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0
}
data class NavPlace(val name:String,val point:NavPoint,val id:String="",val address:String="",val category:String="",val phone:String="",val website:String="",val openingHours:String="",val source:String="")
data class NavManeuver(val point:NavPoint,val type:String,val modifier:String="",val road:String="",val exit:Int?=null)
data class NavStep(val atMeters:Double,val instruction:String,val type:String,val symbol:String="↑",val maneuver:NavManeuver?=null)
data class DrivingRoute(val destination:NavPlace,val points:List<NavPoint>,val cumulative:List<Double>,val distanceMeters:Double,val durationSeconds:Double,val steps:List<NavStep>,val language:String="ko") {
    val geometryMeters get()=cumulative.last()
    val destinationAccessMeters get()=NavigationGeometry.distance(points.last(),destination.point)
}
data class NavFix(val point:NavPoint,val accuracy:Double,val timestampMs:Long,val speedMps:Double?=null,val bearingDegrees:Double?=null)
data class Guidance(
    val route:DrivingRoute?=null,val active:Boolean=false,val tracking:Boolean=false,val arrived:Boolean=false,
    val fix:NavFix?=null,val progressMeters:Double=0.0,val remainingMeters:Double=0.0,val remainingSeconds:Double=0.0,
    val nextInstruction:String="목적지를 검색해 주세요.",val turnSymbol:String="↑",val turnMeters:Double=0.0,val offRoute:Boolean=false,
    val rerouteRequired:Boolean=false,val status:String="전 세계 목적지 검색 · OSM 도로 데이터",
)
object NavigationGeometry {
    fun validateOrigin(route:DrivingRoute,origin:NavPoint) {
        require(origin.valid()&&distance(origin,route.points.first())<=1000) {"출발 위치 근처에 연결 가능한 자동차 도로가 없습니다. 도로 접근 지점을 확인하세요."}
    }
    private fun delta(degrees:Double)=((degrees+540)%360)-180
    fun distance(a:NavPoint,b:NavPoint):Double {
        val p1=a.latitude*PI/180;val p2=b.latitude*PI/180
        val h=sin((p2-p1)/2).pow(2)+cos(p1)*cos(p2)*sin(delta(b.longitude-a.longitude)*PI/360).pow(2)
        return 6_371_000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    fun symbol(m:NavManeuver)=when {
        m.type=="arrive"->"●"
        m.type in listOf("roundabout","rotary")->"⟳"
        m.modifier=="uturn"->"↶"
        m.modifier.contains("left")->"↰"
        m.modifier.contains("right")->"↱"
        else->"↑"
    }
    fun instruction(m:NavManeuver):String {
        val direction=when(m.modifier){"left"->"좌회전";"right"->"우회전";"slight left"->"왼쪽으로 완만하게";"slight right"->"오른쪽으로 완만하게";"sharp left"->"왼쪽으로 크게";"sharp right"->"오른쪽으로 크게";"uturn"->"유턴";else->"직진"}
        val action=when(m.type){"depart"->"경로를 따라 출발";"arrive"->"목적지 도착";"roundabout","rotary"->"회전교차로 ${m.exit?.let { "${it}번째 출구" } ?: "진입"}";"exit roundabout","exit rotary"->"회전교차로에서 $direction";"merge"->"$direction 방향으로 합류";"on ramp"->"$direction 진입로 이용";"off ramp"->"$direction 출구 이용";"fork"->"갈림길에서 $direction";"end of road"->"도로 끝에서 $direction";else->direction}
        return action+if(m.road.isNotBlank())" · ${m.road.take(100)}" else ""
    }
    fun route(destination:NavPlace,points:List<NavPoint>,distance:Double,duration:Double,maneuvers:List<NavManeuver>):DrivingRoute {
        require(destination.point.valid()&&destination.name.isNotBlank())
        require(points.size in 2..150_000&&points.all {it.valid()})
        require(distance.isFinite()&&distance>0&&distance<=30_000_000&&duration.isFinite()&&duration>0&&duration<10_000_000)
        val cumulative=ArrayList<Double>(points.size).apply {add(0.0)}
        for(i in 1 until points.size)cumulative.add(cumulative.last()+NavigationGeometry.distance(points[i-1],points[i]))
        require(cumulative.last()>1&&NavigationGeometry.distance(points.last(),destination.point)<=1000) {"목적지 근처까지 연결되는 자동차 도로가 없습니다."}
        require(maneuvers.size in 1..10_000)
        // Only maneuver anchors need an index. A long road can have 100,000
        // geometry points but just a handful of turns; avoid a map/list per point.
        val anchors=maneuvers.mapTo(HashSet()){it.point}
        val pointIndices=HashMap<NavPoint,MutableList<Int>>()
        points.forEachIndexed { i,p->if(p in anchors)pointIndices.getOrPut(p){mutableListOf()}.add(i) }
        var cursor=0
        var fallbackBudget=2_000_000
        val steps=maneuvers.map { m ->
            require(m.point.valid())
            val indices=pointIndices[m.point]
            val exact=indices?.let {list->
                var low=0;var high=list.size
                while(low<high){val mid=(low+high) ushr 1;if(list[mid]<cursor)low=mid+1 else high=mid}
                list.getOrNull(low)
            }
            var best=exact ?: cursor;var gap=if(exact!=null)0.0 else Double.POSITIVE_INFINITY
            if(exact==null)for(i in cursor..points.lastIndex) {
                require(fallbackBudget-->0) {"복잡한 경로입니다. 더 짧은 구간으로 나누어 검색하세요."}
                val d=NavigationGeometry.distance(m.point,points[i]);if(d<gap){best=i;gap=d}
            }
            require(gap<=1000)
            cursor=best
            NavStep(cumulative[best],instruction(m),m.type,symbol(m),m)
        }
        return DrivingRoute(destination,points,cumulative,distance,duration,steps)
    }
    /** Project onto a polyline, correctly wrapping longitude across the date line. */
    fun project(route:DrivingRoute,p:NavPoint,previous:Double?=null,heading:Double?=null,maxDelta:Double?=null):Pair<Double,Double> {
        var bestGap=Double.POSITIVE_INFINITY;var bestProgress=0.0;var bestScore=Double.POSITIVE_INFINITY
        // Cumulative distances are ordered. Visit only the physically reachable window
        // on a GPS update, including segments that straddle either boundary.
        fun lowerBound(target:Double):Int {
            var low=0;var high=route.cumulative.size
            while(low<high){val mid=(low+high) ushr 1;if(route.cumulative[mid]<target)low=mid+1 else high=mid}
            return low
        }
        val bounded=previous!=null&&maxDelta!=null
        val first=if(bounded)(lowerBound(previous!!-maxDelta!!)-1).coerceIn(0,route.points.lastIndex-1) else 0
        val last=if(bounded)lowerBound(previous!!+maxDelta!!).coerceIn(first,route.points.lastIndex-1) else route.points.lastIndex-1
        val scale=cos(p.latitude*PI/180).coerceAtLeast(.00001)
        for(i in first..last) {
            val a=route.points[i];val b=route.points[i+1]
            val ax=delta(a.longitude-p.longitude)*scale*111_195;val ay=(a.latitude-p.latitude)*111_195
            val bx=ax+delta(b.longitude-a.longitude)*scale*111_195;val by=(b.latitude-p.latitude)*111_195
            val dx=bx-ax;val dy=by-ay;val den=dx*dx+dy*dy
            val t=if(den==0.0)0.0 else (-(ax*dx+ay*dy)/den).coerceIn(0.0,1.0)
            val gap=hypot(ax+t*dx,ay+t*dy)
            val at=route.cumulative[i]+t*(route.cumulative[i+1]-route.cumulative[i])
            if(previous!=null&&maxDelta!=null&&abs(at-previous)>maxDelta)continue
            val headingPenalty=if(heading!=null&&den>25){val segmentHeading=((atan2(dx,dy)*180/PI)+360)%360;40*(abs(delta(segmentHeading-heading))/180).pow(2)} else 0.0
            // At intersections prefer the nearby part of the route, not a later crossing.
            val score=gap+headingPenalty+(if(previous==null)0.0 else abs(at-previous)*.001)
            if(score<bestScore){bestScore=score;bestGap=gap;bestProgress=at}
        }
        return bestProgress to bestGap
    }
}

/** Shared Android/browser live GPS navigation. No simulated fixes are created here. */
class NavigationTracker(private val clock:()->Long=::platformNowMillis) {
    var state=Guidance();private set
    private var nextStepIndex=0
    private var peakProgress=0.0
    private var offSince:Long?=null;private var offCount=0;private var arrivalCount=0
    private var lastRerouteAt:Long?=null
    fun preview(route:DrivingRoute) {state=Guidance(route=route,remainingMeters=route.distanceMeters,remainingSeconds=route.durationSeconds,nextInstruction=route.steps.first().instruction,status="자동차 경로 미리보기 · 실시간 교통 미반영");resetCounters()}
    private fun resetCounters(){nextStepIndex=0;peakProgress=0.0;offSince=null;offCount=0;arrivalCount=0}
    fun usable(fix:NavFix)=fix.point.valid()&&fix.accuracy.isFinite()&&fix.accuracy in 0.0..50.0&&clock()-fix.timestampMs in 0..10_000&&(fix.speedMps==null||(fix.speedMps.isFinite()&&fix.speedMps>=0))&&(fix.bearingDegrees==null||(fix.bearingDegrees.isFinite()&&fix.bearingDegrees in 0.0..360.0))
    fun start(fix:NavFix):Boolean {
        val r=state.route ?: return false
        if(!usable(fix)||NavigationGeometry.distance(r.points.first(),fix.point)>max(45.0,fix.accuracy*2))return false
        state=state.copy(active=true,arrived=false,tracking=false,fix=null,progressMeters=0.0);resetCounters();lastRerouteAt=null
        update(fix);return true
    }
    fun replaceRoute(route:DrivingRoute):Boolean {
        val fix=state.fix ?: return false
        if(!state.active||!usable(fix)||NavigationGeometry.distance(route.points.first(),fix.point)>200)return false
        state=state.copy(route=route,fix=null,progressMeters=0.0,offRoute=false,rerouteRequired=false);resetCounters();update(fix);return true
    }
    fun update(fix:NavFix) {
        if(!state.active)return
        if(!usable(fix)){state=state.copy(tracking=false,status="GPS 정확도·시간을 확인 중 · 회전 안내 일시중지");return}
        if(state.fix!=null && fix.timestampMs<=state.fix!!.timestampMs)return
        val r=state.route ?: return
        val previous=state.fix
        val elapsed=previous?.let {(fix.timestampMs-it.timestampMs)/1000.0} ?: 1.0
        val maximumTravel=max(120.0,elapsed*75+fix.accuracy*2+(previous?.accuracy ?: 0.0)*2)
        if(previous!=null&&NavigationGeometry.distance(previous.point,fix.point)>maximumTravel) {
            state=state.copy(tracking=false,rerouteRequired=false,status="GPS 위치가 크게 바뀌었습니다. 위치 재확인 중 · 회전 안내 중지")
            return
        }
        val heading=fix.bearingDegrees?.takeIf {(fix.speedMps ?: 0.0)>=2}
        val (at,gap)=NavigationGeometry.project(r,fix.point,state.progressMeters,heading,maximumTravel+100)
        val reversing=at<peakProgress-60&&(fix.speedMps ?: 0.0)>=2
        val off=gap>max(45.0,fix.accuracy*2)||reversing
        if(off){if(offSince==null)offSince=clock();offCount++}else{offCount=0;offSince=null}
        val need=off&&offCount>=3&&clock()-(offSince ?: clock())>=5000
        val progress=if(off)state.progressMeters else at
        peakProgress=max(peakProgress,progress)
        val remaining=(r.geometryMeters-progress).coerceAtLeast(0.0)
        val arrived=!off&&remaining<50&&NavigationGeometry.distance(fix.point,r.points.last())<30&&(fix.speedMps ?: 0.0)<8
        arrivalCount=if(arrived)arrivalCount+1 else 0
        val done=arrivalCount>=2
        // Keep the current turn until its point is passed, not twelve metres before it.
        if(!off)while(nextStepIndex<r.steps.lastIndex&&(r.steps[nextStepIndex].type=="depart"||progress>r.steps[nextStepIndex].atMeters+15))nextStepIndex++
        val next=r.steps[nextStepIndex]
        state=state.copy(active=!done,tracking=true,arrived=done,fix=fix,progressMeters=progress,
            remainingMeters=r.distanceMeters*(remaining/r.geometryMeters),remainingSeconds=r.durationSeconds*(remaining/r.geometryMeters),
            turnSymbol=if(done)"●" else next.symbol,nextInstruction=if(done)NavigationLanguages.arrival(r.language,r.destinationAccessMeters) else next.instruction,turnMeters=(next.atMeters-progress).coerceAtLeast(0.0),
            offRoute=off,rerouteRequired=need,status=when{done->"차량 경로 도착 · 안내 종료";off->"경로 이탈 확인 중 · 기존 회전 안내 중지";else->"GPS 주행 안내 · 정확도 약 ${fix.accuracy.toInt()}m · 교통 미반영 예상"})
    }
    fun tick(){if(state.active&&state.fix?.let {usable(it)}!=true)state=state.copy(tracking=false,rerouteRequired=false,status="GPS 신호 대기 · 회전 안내 일시중지")}
    fun takeReroute():Boolean {
        tick();if(!state.rerouteRequired||!state.tracking)return false
        val now=clock();if(lastRerouteAt?.let {now-it<30_000}==true)return false
        lastRerouteAt=now;state=state.copy(rerouteRequired=false);return true
    }
    fun suspendTracking(){if(state.active)state=state.copy(tracking=false,fix=null,rerouteRequired=false,status="화면 밖에서는 GPS 안내 중지 · 복귀 후 위치 재확인")}
    fun stop(){state=Guidance();resetCounters();lastRerouteAt=null}
}
