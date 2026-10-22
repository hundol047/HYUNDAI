package io.cabinos.core

import kotlinx.browser.window
import kotlin.js.json

private val hazardRepository = DemoRoadHazardRepository().apply { seed(platformNowMillis()) }
private val engine = UnifiedVehicleContextEngine(hazards = hazardRepository)
private var sample = VehicleSample(gear=Gear.PARK,parkingReliable=true,charging=true,chargeRemainingSeconds=2400,timestampMs=platformNowMillis())
private var scenario = "충전"
private var appearance=AppearanceSettings()
private var appearanceRestored=false
private fun appearanceSnapshot():String {
    val p=AppearanceCatalog.palette(appearance)
    return JSON.stringify(json("preset" to appearance.preset,"accent" to appearance.accent,"brightness" to appearance.brightness,"nightMap" to appearance.nightMap,
        "colors" to json("background" to AppearanceCatalog.format(p.background),"panel" to AppearanceCatalog.format(p.panel),"line" to AppearanceCatalog.format(p.line),"accent" to AppearanceCatalog.format(p.accent),"text" to AppearanceCatalog.format(p.text),"muted" to AppearanceCatalog.format(p.muted))))
}
private val navigator=NavigationTracker()
private var navFix:NavFix?=null
private var navRevision=0
private var navSafetyHeld=false
private var stationarySince:Long?=null
private fun navSnapshot():String {
    val g=navigator.state
    return JSON.stringify(json("active" to g.active,"tracking" to g.tracking,"arrived" to g.arrived,"hasRoute" to (g.route!=null),
        "destinationAccessMeters" to (g.route?.destinationAccessMeters ?: 0.0),"revision" to navRevision,"destination" to g.route?.destination?.name,"remainingMeters" to g.remainingMeters,"remainingSeconds" to g.remainingSeconds,
        "instruction" to g.nextInstruction,"turnSymbol" to g.turnSymbol,"turnMeters" to g.turnMeters,"offRoute" to g.offRoute,"status" to g.status,"safetyHeld" to navSafetyHeld,
        "fix" to (if(g.active)g.fix else navFix)?.let {json("latitude" to it.point.latitude,"longitude" to it.point.longitude,"accuracy" to it.accuracy,"timestamp" to it.timestampMs.toDouble())}))
}
private fun parseNavRoute(raw:String,name:String,lat:Double,lon:Double):DrivingRoute {
    val json=JSON.parse<dynamic>(raw)
    require(json.code=="Ok") {"자동차로 연결되는 경로가 없습니다."}
    val route=json.routes[0]
    val coordinates=route.geometry.coordinates as Array<dynamic>
    require(coordinates.size in 2..150_000)
    val points=coordinates.map {NavPoint(it[1] as Double,it[0] as Double)}
    val maneuvers=mutableListOf<NavManeuver>()
    for(leg in route.legs as Array<dynamic>)for(step in leg.steps as Array<dynamic>) {
        require(maneuvers.size<10_000)
        val m=step.maneuver
        maneuvers.add(NavManeuver(NavPoint(m.location[1] as Double,m.location[0] as Double),m.type as String,m.modifier as? String ?: "",step.name as? String ?: "",(m.exit as? Double)?.toInt()))
    }
    return NavigationGeometry.route(NavPlace(name.take(240),NavPoint(lat,lon)),points,route.distance as Double,route.duration as Double,maneuvers)
}

private fun select(name: String) {
    scenario=name
    val now=platformNowMillis()
    sample=when(name) {
        "주행" -> sample.copy(speedKph=80.0,gear=Gear.DRIVE,charging=false,connected=true,source=DataSource.SIMULATION)
        "신호 대기" -> sample.copy(speedKph=0.0,gear=Gear.DRIVE,charging=false,connected=true,source=DataSource.SIMULATION)
        "주차" -> sample.copy(speedKph=0.0,gear=Gear.PARK,charging=false,parkingReliable=true,connected=true,source=DataSource.SIMULATION)
        "충전","충전 15분","출발 준비" -> sample.copy(speedKph=0.0,gear=Gear.PARK,parkingReliable=true,connected=true,charging=true,
            chargeRemainingSeconds=when(name){"충전"->2400L;"충전 15분"->900L;else->300L},source=DataSource.SIMULATION)
        "SDK 끊김" -> sample.copy(connected=false)
        "실차 연결" -> VehicleSample(timestampMs=now,connected=false,source=DataSource.VEHICLE)
        else -> sample
    }.copy(timestampMs=now)
    engine.acceptVehicle(sample)
}
private fun snapshot(): String {
    val s=engine.state.value
    val session=s.session
    return JSON.stringify(json(
        "context" to s.context.name,"label" to UnifiedVehicleContextEngine.contextLabel(s.context),"parked" to s.parkedAllowed,
        "echoRoad" to s.echoRoadActive,"source" to s.sample?.source?.name,"scenario" to scenario,"speed" to (s.sample?.speedKph ?: 0.0),
        "charging" to (s.sample?.charging==true),"battery" to (s.sample?.batteryPercent ?: 0),"connected" to (s.sample?.connected==true),
        "waiting" to s.schedule.waitingSeconds.toDouble(),"available" to s.schedule.availableSeconds.toDouble(),"buffer" to s.preferences.departureBufferMinutes,
        "departure" to s.schedule.departureAtMs?.toDouble(),"estimate" to s.schedule.estimateLabel,"network" to s.networkAvailable,"location" to s.locationAvailable,
        "origin" to json("latitude" to s.activityOrigin.latitude,"longitude" to s.activityOrigin.longitude,"label" to s.activityOrigin.label),
        "nearbyRevision" to s.nearbyRevision.toDouble(),"selectedActivity" to s.selectedActivityId,
        "message" to s.message,"focus" to s.preferences.focusMinutes,"relax" to s.preferences.relaxMinutes,"voice" to s.preferences.voiceEnabled,"category" to s.preferences.preferredCategory,
        "session" to session?.let { json("id" to it.id,"kind" to it.kind.name,"status" to it.status.name,"remaining" to it.remainingSeconds.toDouble(),"duration" to it.durationSeconds.toDouble()) },
        "hazards" to s.hazards.map { json("id" to it.hazard.id,"kind" to it.hazard.kind,"distance" to it.distanceMeters,"confidence" to (it.hazard.confidence*100).toInt()) }.toTypedArray(),
        "activities" to s.activities.map { json("id" to it.activity.id,"name" to it.activity.name,"category" to it.activity.category,"walk" to it.activity.oneWaySeconds.toDouble(),"usable" to it.usableSeconds.toDouble(),"feasible" to it.feasible,"latitude" to it.activity.latitude,"longitude" to it.activity.longitude,"simulated" to it.activity.simulated) }.toTypedArray(),
        "history" to s.history.map { json("kind" to it.kind.name,"duration" to it.durationSeconds.toDouble(),"remaining" to it.remainingSeconds.toDouble(),"status" to it.status.name) }.toTypedArray(),
        "events" to s.events.takeLast(8).map { json("id" to it.id.toDouble(),"name" to it.name,"message" to it.message) }.toTypedArray()
    ))
}
fun main() {
    val bridge=js("({})")
    bridge.state={ snapshot() }
    bridge.appearance={appearanceSnapshot()}
    bridge.setAppearance={preset:String,accent:String,brightness:String,night:Boolean ->
        if(engine.state.value.parkedAllowed){appearance=AppearanceCatalog.normalize(AppearanceSettings(preset,accent,brightness,night));true}else false
    }
    // Restore visual preferences only: this never establishes parked/vehicle trust.
    bridge.restoreAppearance={preset:String,accent:String,brightness:String,night:Boolean -> if(!appearanceRestored){appearanceRestored=true;appearance=AppearanceCatalog.normalize(AppearanceSettings(preset,accent,brightness,night))}}
    bridge.navState={navSnapshot()}
    bridge.navGeometry={JSON.stringify(navigator.state.route?.points?.map {arrayOf(it.latitude,it.longitude)}?.toTypedArray())}
    bridge.navFix={lat:Double,lon:Double,accuracy:Double,timestamp:Double,speed:Double,bearing:Double ->
        val f=NavFix(NavPoint(lat,lon),accuracy,timestamp.toLong(),if(speed.isFinite()&&speed>=0)speed else null,if(bearing.isFinite()&&bearing>=0)bearing else null)
        if(navigator.usable(f)) {
            navFix=f
            if(f.speedMps?.let {it>=2}==true){navSafetyHeld=true;stationarySince=null;engine.setNavigationLock(true)}
            if(navSafetyHeld&&!navigator.state.active) {
                if(f.speedMps?.let {it in 0.0..0.5}==true) {
                    if(stationarySince==null)stationarySince=platformNowMillis()
                    if(platformNowMillis()-(stationarySince ?: platformNowMillis())>=5000){navSafetyHeld=false;engine.setNavigationLock(false)}
                } else stationarySince=null
            }
        }
        navigator.update(f);navSnapshot()
    }
    bridge.navPreview={raw:String,name:String,lat:Double,lon:Double ->
        try {
            require(engine.state.value.parkedAllowed&&!navigator.state.active)
            val r=parseNavRoute(raw,name,lat,lon)
            val f=navFix
            require(f!=null&&navigator.usable(f)&&NavigationGeometry.distance(f.point,r.points.first())<=1000) {"출발 위치 근처 자동차 경로를 확인하세요."}
            navigator.preview(r);navRevision++;""
        }catch(e:Exception){e.message ?: "경로 응답을 확인하세요."}
    }
    bridge.navReplace={raw:String ->
        try {val d=navigator.state.route!!.destination;val r=parseNavRoute(raw,d.name,d.point.latitude,d.point.longitude);val ok=navigator.replaceRoute(r);if(ok)navRevision++;ok}catch(_:Exception){false}
    }
    bridge.navStart={accessConfirmed:Boolean ->
        val f=navFix
        val started=engine.state.value.parkedAllowed&&f!=null&&((navigator.state.route?.destinationAccessMeters ?: 0.0)<=100||accessConfirmed==true)&&navigator.start(f)
        if(started){navSafetyHeld=true;stationarySince=null;engine.setNavigationLock(true)}
        started
    }
    bridge.navReroute={navigator.takeReroute()}
    bridge.navBackground={navFix=null;navigator.suspendTracking()}
    bridge.navStop={navigator.stop();navRevision++;if(!navSafetyHeld){navFix=null;engine.setNavigationLock(false)}}
    bridge.tick={
        appearanceRestored=true
        navigator.tick()
        sample=sample.copy(timestampMs=platformNowMillis())
        engine.acceptVehicle(sample)
        snapshot()
    }
    bridge.vehicle={ name:String -> select(name); snapshot() }
    bridge.command={ text:String -> engine.assistant(text) }
    bridge.start={ kind:String,minutes:Int -> engine.stayInCabin();engine.startSession(SessionKind.valueOf(kind),minutes) }
    bridge.pause={ engine.pauseOrResume() }
    bridge.end={ engine.endSession() }
    bridge.waiting={ minutes:Int -> engine.setWaitingMinutes(minutes) }
    bridge.activity={ id:String -> engine.selectActivity(id) }
    bridge.mapOrigin={ lat:Double,lon:Double,label:String -> engine.updateNearbyActivities(MapPosition(lat,lon,label.take(100)),emptyList()) }
    bridge.mapExamples={ engine.updateNearbyActivities(MapPosition(),DemoActivityRepository().activities()) }
    bridge.places={ lat:Double,lon:Double,label:String,raw:String ->
        val origin=MapPosition(lat,lon,label.take(100))
        val data=JSON.parse<Array<dynamic>>(raw)
        val places=data.take(60).mapNotNull { p ->
            val category=p.category as? String ?: ""
            if(category !in listOf("카페","식당","산책","쇼핑"))null
            else NearbyGeometry.osmActivity(p.id as? String ?: "",p.name as? String ?: "",category,p.latitude as? Double ?: Double.NaN,p.longitude as? Double ?: Double.NaN,origin)
        }
        engine.updateNearbyActivities(origin,places)
    }
    bridge.category={ value:String -> if(value in listOf("전체","카페","식당","산책","쇼핑"))engine.updatePreferences(engine.state.value.preferences.copy(preferredCategory=value)) }
    bridge.hazard={ engine.injectHazard() }
    bridge.network={ enabled:Boolean -> engine.setConnectivity(network=enabled) }
    bridge.location={ enabled:Boolean -> engine.setConnectivity(location=enabled) }
    bridge.route={ alternate:Boolean -> engine.updateRoute(Route(if(alternate)"demo-detour" else "demo-pangyo")) }
    bridge.preferences={ focus:Int,relax:Int,buffer:Int -> engine.updatePreferences(engine.state.value.preferences.copy(focusMinutes=focus,relaxMinutes=relax,departureBufferMinutes=buffer)) }
    bridge.clear={ if(engine.clearPersonalData()){appearance=AppearanceSettings();navigator.stop();navFix=null;navRevision++;navSafetyHeld=false;stationarySince=null;engine.setNavigationLock(false)} }
    bridge.background={ engine.invalidateTelemetry("브라우저를 벗어나 세션을 일시정지했습니다.") }
    window.asDynamic().CabinOS=bridge
    select("충전")
}
