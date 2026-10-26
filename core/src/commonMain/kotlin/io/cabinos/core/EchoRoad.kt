package io.cabinos.core

import kotlin.math.abs

/** GPS evidence is a candidate, never proof of collision, road defect or driver fault. */
data class BrakeCandidate(
    val point: NavPoint,
    val heading: Double,
    val atMs: Long,
    val deceleration: Double,
)

class BrakeDetector {
    private var previous: NavFix? = null
    private var count = 0
    private var drop = 0.0
    private var lastReport = Long.MIN_VALUE / 2

    fun reset() {
        previous = null
        count = 0
        drop = 0.0
    }

    fun accept(f: NavFix, now: Long): BrakeCandidate? {
        val speed = f.speedMps
        val heading = f.bearingDegrees
        if (
            !f.point.valid() ||
                !f.accuracy.isFinite() ||
                f.accuracy !in 0.0..10.0 ||
                now - f.timestampMs !in 0..2000 ||
                speed == null ||
                !speed.isFinite() ||
                speed !in 0.0..70.0 ||
                heading == null ||
                !heading.isFinite() ||
                heading !in 0.0..359.999
        ) {
            reset()
            return null
        }
        val p = previous
        previous = f
        if (p == null) return null
        val dt = (f.timestampMs - p.timestampMs) / 1000.0
        if (dt !in 0.5..2.0 || abs((heading - p.bearingDegrees!! + 540) % 360 - 180) > 25) {
            count = 0
            drop = 0.0
            return null
        }
        val displacement = NavigationGeometry.distance(p.point, f.point)
        val expected = (p.speedMps!! + speed) * 0.5 * dt
        if (abs(displacement - expected) > maxOf(8.0, expected * 0.5)) {
            count = 0
            drop = 0.0
            return null
        }
        val decel = (p.speedMps!! - speed) / dt
        if (decel !in 3.0..15.0 || (count == 0 && p.speedMps!! < 8)) {
            count = 0
            drop = 0.0
            return null
        }
        count++
        drop += p.speedMps!! - speed
        if (count < 2 || drop < 5 || now - lastReport < 30000) return null
        count = 0
        drop = 0.0
        lastReport = now
        return BrakeCandidate(f.point, heading, f.timestampMs, decel)
    }
}

data class EchoRisk(
    val id: String,
    val point: NavPoint,
    val heading: Double?,
    val expiresAtMs: Long,
    val label: String,
    val source: String,
    val reporters: Int = 0,
)

object EchoRoadMatch {
    fun ahead(g: Guidance, risks: List<EchoRisk>, now: Long): Pair<EchoRisk, Int>? {
        val f = g.fix ?: return null
        val route = g.route ?: return null
        if (
            !g.active ||
                !g.tracking ||
                g.offRoute ||
                g.directionUncertain ||
                now - f.timestampMs !in 0..3000 ||
                !f.accuracy.isFinite() ||
                f.accuracy !in 0.0..15.0
        )
            return null
        return risks
            .asSequence()
            .filter { it.expiresAtMs > now && it.point.valid() }
            .mapNotNull { r ->
                if (
                    r.heading != null &&
                        f.bearingDegrees?.let { abs((it - r.heading + 540) % 360 - 180) > 40 } !=
                            false
                )
                    return@mapNotNull null
                val m =
                    NavigationGeometry.match(
                        route,
                        r.point,
                        previous = g.progressMeters + 400,
                        maxDelta = 450.0,
                        continuityWeight = 0.0,
                        maxGap = 15.0,
                        ambiguityRadius = 15.0,
                    )
                val ahead = m.progressMeters - g.progressMeters
                if (m.gapMeters > 15 || m.directionUncertain || ahead !in 40.0..700.0) null
                else r to ahead.toInt()
            }
            .minByOrNull { it.second }
    }
}
