package io.cabinos.core

/** Both directions are routed separately; no assumption that a return path is symmetric. */
data class WalkingPlan(
    val origin: MapPosition,
    val outboundSeconds: Long,
    val inboundSeconds: Long,
    val distanceMeters: Double,
    val points: List<MapPosition>,
    val checkedAtMs: Long,
) {
    val roundTripSeconds: Long
        get() = outboundSeconds + inboundSeconds

    fun usable(now: Long, from: MapPosition): Boolean =
        origin.valid() &&
            from.valid() &&
            NearbyGeometry.distanceMeters(origin, from) <= 10 &&
            now - checkedAtMs in 0..900_000 &&
            outboundSeconds in 60..14_400 &&
            inboundSeconds in 60..14_400 &&
            distanceMeters.isFinite() &&
            distanceMeters in 0.0..30_000.0 &&
            points.size in 2..10_000 &&
            points.all { it.valid() }
}
