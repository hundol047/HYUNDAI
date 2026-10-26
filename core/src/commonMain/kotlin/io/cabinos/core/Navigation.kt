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
    val rerouteRequired:Boolean=false,val directionUncertain:Boolean=false,val status:String="전 세계 목적지 검색 · OSM 도로 데이터",
    val maneuverIndex:Int=0,
)
data class RouteProjection(val progressMeters:Double,val gapMeters:Double,val directionUncertain:Boolean=false)
object NavigationGeometry {
    /** Keep provider destination signs, especially for unnamed motorway ramps. */
    fun roadLabel(name:String,destinations:String="",reference:String=""):String {
        fun clean(value:String)=value.filter {it>=' '&&it!='\u007f'&&it !in '\u202a'..'\u202e'&&it !in '\u2066'..'\u2069'}.trim().take(200)
        return listOf(clean(name),clean(destinations)).filter {it.isNotBlank()}.distinct().joinToString(" · ").ifBlank {clean(reference)}.take(300)
    }
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
        // Public OSRM profiles can return "right" even in left-driving countries.
        // The supplied exit number is useful; an assumed rotation direction is not.
        m.type in listOf("roundabout","rotary")->"◎"
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
    fun project(route:DrivingRoute,p:NavPoint,previous:Double?=null,heading:Double?=null,maxDelta:Double?=null,headingWeight:Double=40.0,continuityWeight:Double=.2,expectedProgress:Double?=previous,maxGap:Double=Double.POSITIVE_INFINITY):Pair<Double,Double> = match(route,p,previous,heading,maxDelta,headingWeight,continuityWeight,expectedProgress,maxGap).let {it.progressMeters to it.gapMeters}
    fun match(route:DrivingRoute,p:NavPoint,previous:Double?=null,heading:Double?=null,maxDelta:Double?=null,headingWeight:Double=40.0,continuityWeight:Double=.2,expectedProgress:Double?=previous,maxGap:Double=Double.POSITIVE_INFINITY,ambiguityRadius:Double=0.0):RouteProjection {
        var bestGap=Double.POSITIVE_INFINITY;var bestProgress=0.0;var bestScore=Double.POSITIVE_INFINITY
        val nearbyCandidates=if(ambiguityRadius>0)mutableListOf<Double>() else null
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
            // Repeated vertices are not directionless alternative roads. Short
            // nonzero OSRM segments still carry a direction around hairpins.
            if(den<.000001)continue
            val t=if(den==0.0)0.0 else (-(ax*dx+ay*dy)/den).coerceIn(0.0,1.0)
            val gap=hypot(ax+t*dx,ay+t*dy)
            val at=route.cumulative[i]+t*(route.cumulative[i+1]-route.cumulative[i])
            if(previous!=null&&maxDelta!=null&&abs(at-previous)>maxDelta)continue
            val segmentHeading=if(heading!=null)((atan2(dx,dy)*180/PI)+360)%360 else 0.0
            if(nearbyCandidates!=null&&gap<=ambiguityRadius)nearbyCandidates.add(at)
            val headingPenalty=if(heading!=null)headingWeight*(abs(delta(segmentHeading-heading))/180) else 0.0
            // At intersections prefer the nearby part of the route, not a later crossing.
            val score=gap+headingPenalty+(if(previous==null)0.0 else abs(at-(expectedProgress ?: previous))*continuityWeight)
            // Continuity must not select an implausibly distant segment when a
            // geographically plausible candidate exists inside the same window.
            val plausible=gap<=maxGap;val bestPlausible=bestGap<=maxGap
            if((plausible&&!bestPlausible)||(plausible==bestPlausible&&score<bestScore)){bestScore=score;bestGap=gap;bestProgress=at}
        }
        val ambiguous=nearbyCandidates?.any {at->abs(at-bestProgress)>max(45.0,ambiguityRadius*2)}==true
        return RouteProjection(bestProgress,bestGap,ambiguous)
    }
}

/** Shared Android/browser live GPS navigation. No simulated fixes are created here. */
class NavigationTracker(private val clock:()->Long=::platformNowMillis) {
    var state=Guidance();private set
    private var nextStepIndex=0
    private var peakConfirmedProgress=0.0
    private val progressHistory=ArrayDeque<Pair<Long,Double>>()
    private var lastOnRouteAt:Long?=null
    private var offSince:Long?=null;private var offCount=0;private var arrivalCount=0
    private var lastRerouteAt:Long?=null
    fun preview(route:DrivingRoute) {state=Guidance(route=route,remainingMeters=route.distanceMeters,remainingSeconds=route.durationSeconds,nextInstruction=route.steps.first().instruction,status="자동차 경로 미리보기 · 실시간 교통 미반영");resetCounters()}
    private fun resetCounters(){nextStepIndex=0;peakConfirmedProgress=0.0;progressHistory.clear();lastOnRouteAt=null;offSince=null;offCount=0;arrivalCount=0}
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
        if(!usable(fix)){arrivalCount=0;state=state.copy(tracking=false,status="GPS 정확도·시간을 확인 중 · 회전 안내 일시중지");return}
        if(state.fix!=null && fix.timestampMs<=state.fix!!.timestampMs)return
        val r=state.route ?: return
        val previous=state.fix
        val elapsed=previous?.let {(fix.timestampMs-it.timestampMs)/1000.0} ?: 1.0
        val maximumTravel=max(120.0,elapsed*75+fix.accuracy*2+(previous?.accuracy ?: 0.0)*2)
        if(previous!=null&&NavigationGeometry.distance(previous.point,fix.point)>maximumTravel) {
            arrivalCount=0
            state=state.copy(tracking=false,rerouteRequired=false,status="GPS 위치가 크게 바뀌었습니다. 위치 재확인 중 · 회전 안내 중지")
            return
        }
        val heading=fix.bearingDegrees?.takeIf {(fix.speedMps ?: 0.0)>=2}
        // Progress is frozen while off route. Its reachable window must grow
        // from the last accepted route position, not from the last off-road fix.
        val projectionElapsed=max(elapsed,lastOnRouteAt?.let {(fix.timestampMs-it)/1000.0} ?: elapsed)
        val maximumProgress=max(120.0,projectionElapsed*75+fix.accuracy*2+(previous?.accuracy ?: 0.0)*2)+100
        // Precise fixes dominate position matching; uncertain fixes rely more
        // on recent progress and course instead of jumping between close bends.
        val continuityWeight=((fix.accuracy-5)/90).coerceIn(.001,.5)
        // Some browser GPS providers omit both speed and course. A short
        // regression of accepted positions supplies only a matching prior;
        // it never creates a fix or advances progress without fresh geometry.
        val matchingSpeed=fix.speedMps ?: recentProgressSpeed(fix.timestampMs)
        val uncertainCourse=heading==null&&fix.accuracy>10&&(matchingSpeed ?: 0.0) in 2.0..75.0&&previous!=null&&!state.offRoute
        val expected=state.progressMeters+if(uncertainCourse)matchingSpeed!!*elapsed.coerceAtMost(3.0) else 0.0
        val projection=NavigationGeometry.match(r,fix.point,state.progressMeters,heading,maximumProgress,max(40.0,fix.accuracy*2),if(uncertainCourse).65 else continuityWeight,expected,max(45.0,fix.accuracy*2),if(heading==null)fix.accuracy+5 else 0.0)
        val at=projection.progressMeters;val gap=projection.gapMeters;val ambiguous=projection.directionUncertain
        val reversing=!ambiguous&&at+fix.accuracy<peakConfirmedProgress-60&&(fix.speedMps ?: 0.0)>=2
        val off=gap>max(45.0,fix.accuracy*2)||reversing
        if(off){if(offSince==null)offSince=clock();offCount++}else{offCount=0;offSince=null}
        val need=off&&offCount>=3&&clock()-(offSince ?: clock())>=5000
        val progress=if(off)state.progressMeters else at
        // Compare non-overlapping uncertainty bounds before calling motion a reversal.
        // A noisy accepted fix can otherwise set a false peak for every later fix.
        // Ambiguous matches are not a new confirmed anchor or speed observation.
        // Otherwise a close hairpin can repeatedly renew the wrong search window.
        if(!off&&!ambiguous){peakConfirmedProgress=max(peakConfirmedProgress,progress-fix.accuracy);lastOnRouteAt=fix.timestampMs
            // Bound both memory and per-fix work even for high-frequency providers.
            if(progressHistory.isEmpty()||fix.timestampMs-progressHistory.last().first>=250)progressHistory.addLast(fix.timestampMs to progress)
            while(progressHistory.isNotEmpty()&&(fix.timestampMs-progressHistory.first().first>12_000||progressHistory.size>64))progressHistory.removeFirst()
        }
        val remaining=(r.geometryMeters-progress).coerceAtLeast(0.0)
        val destinationGap=NavigationGeometry.distance(fix.point,r.points.last())
        val nearDestination=!off&&!ambiguous&&remaining<50&&destinationGap<30&&(fix.speedMps ?: 0.0)<8
        val arrived=nearDestination&&destinationGap+fix.accuracy<30
        arrivalCount=if(arrived)arrivalCount+1 else 0
        val done=arrivalCount>=2
        // Keep the current turn until its point is passed, not twelve metres before it.
        if(!off&&!ambiguous)while(nextStepIndex<r.steps.lastIndex&&(r.steps[nextStepIndex].type=="depart"||progress>r.steps[nextStepIndex].atMeters+15))nextStepIndex++
        val next=r.steps[nextStepIndex]
        state=state.copy(active=!done,tracking=true,arrived=done,fix=fix,progressMeters=progress,
            remainingMeters=r.distanceMeters*(remaining/r.geometryMeters),remainingSeconds=r.durationSeconds*(remaining/r.geometryMeters),
            turnSymbol=if(done)"●" else next.symbol,nextInstruction=when {
                ambiguous->"GPS 위치·진행 방향 확인 중"
                done->NavigationLanguages.arrival(r.language,r.destinationAccessMeters)
                next.type=="arrive"->NavigationLanguages.upcomingArrival(r.language,next.maneuver?.road.orEmpty())
                else->next.instruction
            },turnMeters=(next.atMeters-progress).coerceAtLeast(0.0),maneuverIndex=nextStepIndex,
            offRoute=off,rerouteRequired=need,directionUncertain=ambiguous,status=when{ambiguous->"GPS 오차 안에 여러 도로가 있습니다 · 회전 안내 대기";done->"차량 경로 도착 · 안내 종료";off->"경로 이탈 확인 중 · 기존 회전 안내 중지";nearDestination&&!arrived->"목적지 부근 · 정확한 GPS를 기다리는 중";else->"GPS 주행 안내 · 정확도 약 ${fix.accuracy.toInt()}m · 교통 미반영 예상"})
    }
    private fun recentProgressSpeed(timestamp:Long):Double? {
        if(progressHistory.size<4||timestamp-progressHistory.last().first>5000)return null
        val span=(progressHistory.last().first-progressHistory.first().first)/1000.0
        if(span<4)return null
        val first=progressHistory.first().first
        val meanTime=progressHistory.map {(it.first-first)/1000.0}.average()
        val meanProgress=progressHistory.map {it.second}.average()
        var covariance=0.0;var variance=0.0
        for((at,progress) in progressHistory){val dt=(at-first)/1000.0-meanTime;covariance+=dt*(progress-meanProgress);variance+=dt*dt}
        return (covariance/variance).takeIf {it.isFinite()&&it in 0.0..75.0}
    }
    fun tick(){if(state.active&&state.fix?.let {usable(it)}!=true){arrivalCount=0;state=state.copy(tracking=false,rerouteRequired=false,status="GPS 신호 대기 · 회전 안내 일시중지")}}
    fun takeReroute():Boolean {
        tick();if(!state.rerouteRequired||!state.tracking)return false
        val now=clock();if(lastRerouteAt?.let {now-it<30_000}==true)return false
        lastRerouteAt=now;state=state.copy(rerouteRequired=false);return true
    }
    fun suspendTracking(){arrivalCount=0;progressHistory.clear();if(state.active)state=state.copy(tracking=false,fix=null,rerouteRequired=false,status="화면 밖에서는 GPS 안내 중지 · 복귀 후 위치 재확인")}
    fun stop(){state=Guidance();resetCounters();lastRerouteAt=null}
}
