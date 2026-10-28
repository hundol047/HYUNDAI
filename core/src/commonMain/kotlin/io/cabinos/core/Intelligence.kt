package io.cabinos.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Safe physical state is evaluated before contextual substates. Missing telemetry fails closed. */
class SafetyDecisionEngine : ContextDecisionEngine {
    override fun decide(sample: VehicleSample?, nowMs: Long, safeParkingConfirmed: Boolean): VehicleContext {
        if (sample == null || !sample.connected || nowMs - sample.timestampMs !in 0..5_000 ||
            !sample.speedKph.isFinite() || sample.speedKph < 0) return VehicleContext.UNKNOWN
        if (sample.speedKph > 0.0) return VehicleContext.DRIVING
        if (sample.gear == Gear.DRIVE || sample.gear == Gear.REVERSE) return VehicleContext.STOPPED_IN_TRAFFIC
        if (sample.gear != Gear.PARK || !sample.parkingReliable || !safeParkingConfirmed) return VehicleContext.UNKNOWN
        return if (sample.charging) VehicleContext.CHARGING else VehicleContext.PARKED
    }
}

class HazardRanker {
    fun rank(events: List<RoadHazard>, route: Route, nowMs: Long): List<RankedHazard> = events
        .filter { it.reportedAtMs <= nowMs && it.expiresAtMs > nowMs && it.confidence >= .55 && it.routeId == route.id }
        .filter { abs(((it.heading - route.heading + 540) % 360) - 180) <= 60 }
        .filter { it.routeMeters - route.progressMeters in 0.0..10_000.0 }
        .groupBy { it.id }.values.map { group -> group.maxBy { it.reportedAtMs } }
        .map { e ->
            val distance = e.routeMeters - route.progressMeters
            val freshness = (1.0 - (nowMs - e.reportedAtMs).toDouble() / max(1, e.expiresAtMs - e.reportedAtMs)).coerceIn(0.0, 1.0)
            RankedHazard(e, distance, (e.severity.coerceIn(0.0, 1.0) * .30 + freshness * .15 + e.confidence.coerceIn(0.0, 1.0) * .25 +
                (1 - distance / 10_000) * .20 + min(e.reports.coerceAtLeast(0), 5) / 5.0 * .10))
        }.sortedByDescending { it.score }
}

class HazardAlertGate(private val cooldownMs: Long = 60_000) {
    private val announced = mutableMapOf<String, Long>()
    private var lastAnnouncement: Long? = null
    fun take(hazard: RankedHazard?, nowMs: Long): Boolean {
        if (hazard == null || hazard.distanceMeters > 3_000) return false
        announced.entries.removeAll { nowMs - it.value >= 30 * 60_000 }
        if (lastAnnouncement?.let { nowMs - it < cooldownMs } == true || announced.containsKey(hazard.hazard.id)) return false
        announced[hazard.hazard.id] = nowMs; lastAnnouncement = nowMs; return true
    }
}

object TimeOptimizer {
    fun calculate(departureAtMs: Long?, nowMs: Long, bufferSeconds: Long, returnSeconds: Long = 0, label: String = "사용자 설정"): SharedSchedule {
        val remaining = departureAtMs?.let { ((it - nowMs) / 1000).coerceAtLeast(0) } ?: 0
        return SharedSchedule(departureAtMs, remaining, bufferSeconds.coerceAtLeast(0), returnSeconds.coerceAtLeast(0),
            (remaining - bufferSeconds.coerceAtLeast(0) - returnSeconds.coerceAtLeast(0)).coerceAtLeast(0), label)
    }
    fun recommend(activities: List<NearbyActivity>, schedule: SharedSchedule, category: String): List<ActivityOption> = activities
        .filter { category == "전체" || it.category == category }
        .map { a ->
            val usable = (schedule.waitingSeconds - schedule.bufferSeconds - a.roundTripSeconds).coerceAtLeast(0)
            ActivityOption(a, usable, (a.simulated || a.walking != null) && usable >= a.minimumActivitySeconds)
        }.sortedWith(compareByDescending<ActivityOption> { it.feasible }.thenBy { it.activity.oneWaySeconds })
}
