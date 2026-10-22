package io.cabinos.core

import kotlinx.browser.window
import kotlin.js.json

private val hazardRepository = DemoRoadHazardRepository().apply { seed(platformNowMillis()) }
private val engine = UnifiedVehicleContextEngine(hazards = hazardRepository)
private var sample = VehicleSample(gear=Gear.PARK,parkingReliable=true,charging=true,chargeRemainingSeconds=2400,timestampMs=platformNowMillis())
private var scenario = "충전"

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
    bridge.tick={
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
    bridge.clear={ engine.clearPersonalData() }
    bridge.background={ engine.invalidateTelemetry("브라우저를 벗어나 세션을 일시정지했습니다.") }
    window.asDynamic().CabinOS=bridge
    select("충전")
}
