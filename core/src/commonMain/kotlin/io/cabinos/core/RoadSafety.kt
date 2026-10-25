package io.cabinos.core

import kotlin.math.floor

enum class RoadSafetyKind {
    BUMP,
    CAMERA,
}

data class RoadSafetyPoint(
    val id: String,
    val point: NavPoint,
    val kind: RoadSafetyKind,
    val road: String,
    val recordDate: String,
    val expiresAtMs: Long,
    val reportedSpeedKmh: Int? = null,
    val validFromMs: Long = 0,
)

data class RoadSafetyApproach(val item: RoadSafetyPoint, val meters: Double)

/** Offline reference points, not lane/direction-verified enforcement information. */
class RoadSafetyIndex(points: List<RoadSafetyPoint>) {
    private val cells = points.filter { it.point.valid() }.groupBy { cell(it.point) }

    private fun cell(point: NavPoint) =
        floor(point.latitude * 100).toInt() to floor(point.longitude * 100).toInt()

    fun nearby(fix: NavFix?, now: Long): List<RoadSafetyPoint> {
        if (
            fix == null ||
                !fix.point.valid() ||
                !fix.accuracy.isFinite() ||
                fix.accuracy !in 0.0..20.0 ||
                now - fix.timestampMs !in 0..10_000
        )
            return emptyList()
        val (lat, lon) = cell(fix.point)
        return (-1..1)
            .flatMap { y -> (-1..1).flatMap { x -> cells[lat + y to lon + x].orEmpty() } }
            .filter {
                now in it.validFromMs until it.expiresAtMs &&
                    NavigationGeometry.distance(fix.point, it.point) <= 600
            }
            .sortedBy { NavigationGeometry.distance(fix.point, it.point) }
            .take(32)
    }

    fun approach(g: Guidance, nearby: List<RoadSafetyPoint>, now: Long): RoadSafetyApproach? {
        val route = g.route ?: return null
        val fix = g.fix ?: return null
        if (
            !g.active ||
                !g.tracking ||
                g.offRoute ||
                g.directionUncertain ||
                !fix.accuracy.isFinite() ||
                fix.accuracy !in 0.0..20.0 ||
                now - fix.timestampMs !in 0..10_000 ||
                fix.speedMps?.let { it.isFinite() && it >= 2 } != true
        )
            return null
        return nearby
            .asSequence()
            // Camera direction is not a compass bearing in the public source.
            // Show reference locations, but never announce an applicable speed limit.
            .filter { it.kind == RoadSafetyKind.BUMP && now in it.validFromMs until it.expiresAtMs }
            .mapNotNull { item ->
                val match =
                    NavigationGeometry.match(
                        route,
                        item.point,
                        previous = g.progressMeters + 150,
                        maxDelta = 200.0,
                        continuityWeight = 0.0,
                        maxGap = 12.0,
                        ambiguityRadius = 15.0,
                    )
                val ahead = match.progressMeters - g.progressMeters
                if (match.gapMeters > 12 || match.directionUncertain || ahead !in 40.0..250.0) null
                else RoadSafetyApproach(item, ahead)
            }
            .minByOrNull { it.meters }
    }
}

/** One advisory per point per trip; turn instructions retain priority. */
class RoadSafetyAnnouncements {
    private val spoken = mutableSetOf<String>()
    private var lastAt: Long? = null

    fun reset() {
        spoken.clear()
        lastAt = null
    }

    fun eligible(item: RoadSafetyApproach, now: Long, turnMeters: Double): Boolean =
        item.item.id !in spoken && turnMeters > 150 && (lastAt == null || now - lastAt!! >= 15_000)

    fun accepted(item: RoadSafetyApproach, now: Long) {
        spoken += item.item.id
        lastAt = now
    }
}
