package io.cabinos.core

import kotlin.math.floor
import kotlin.math.roundToInt

enum class RoadSafetyKind {
    BUMP,
    CAMERA,
}

enum class CameraAdvisory {
    NONE,
    FIXED,
    SECTION,
    SECTION_START,
    SECTION_END,
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
    val cameraAdvisory: CameraAdvisory = CameraAdvisory.NONE,
)

data class RoadSafetyApproach(val item: RoadSafetyPoint, val meters: Double) {
    val roundedMeters: Int
        get() {
            val step = if (meters < 300) 10 else 50
            return ((meters / step).roundToInt() * step).coerceAtLeast(10)
        }

    fun label(korean: Boolean = true): String =
        if (item.kind == RoadSafetyKind.BUMP) {
            if (korean) "과속방지턱" else "speed bump"
        } else
            when (item.cameraAdvisory) {
                CameraAdvisory.SECTION_START ->
                    if (korean) "구간단속 시작 지점" else "section enforcement start point"
                CameraAdvisory.SECTION_END ->
                    if (korean) "구간단속 종료 지점" else "section enforcement end point"
                CameraAdvisory.SECTION -> if (korean) "구간단속카메라" else "section enforcement camera"
                else -> if (korean) "고정식 단속카메라" else "fixed enforcement camera"
            }

    fun spoken(korean: Boolean): String {
        val caution =
            if (item.kind == RoadSafetyKind.CAMERA) {
                if (korean) "적용 방향은 현장 표지를 확인하세요." else "Check signs for the applicable direction."
            } else if (korean) "현장 표지를 확인하세요." else "Check road signs."
        return if (korean) "약 ${roundedMeters}미터 앞 경로 주변에 ${label()} 등록 지점이 있습니다. $caution"
        else
            "In about $roundedMeters meters, a ${label(false)} is recorded near the route. $caution"
    }
}

/** Offline reference points, not lane/direction-verified enforcement information. */
class RoadSafetyIndex(points: List<RoadSafetyPoint>) {
    private val cells = points.filter { it.point.valid() }.groupBy { cell(it.point) }

    private fun cell(point: NavPoint) =
        floor(point.latitude * 100).toInt() to floor(point.longitude * 100).toInt()

    fun nearby(fix: NavFix?, now: Long): List<RoadSafetyPoint> =
        candidates(fix, now)
            .sortedBy { NavigationGeometry.distance(fix!!.point, it.point) }
            .take(32)

    fun upcoming(g: Guidance, now: Long): List<RoadSafetyApproach> =
        matches(g, candidates(g.fix, now), now)

    private fun candidates(fix: NavFix?, now: Long): List<RoadSafetyPoint> {
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
    }

    fun approach(g: Guidance, nearby: List<RoadSafetyPoint>, now: Long): RoadSafetyApproach? =
        matches(g, nearby, now).firstOrNull()

    private fun matches(
        g: Guidance,
        nearby: List<RoadSafetyPoint>,
        now: Long,
    ): List<RoadSafetyApproach> {
        val route = g.route ?: return emptyList()
        val fix = g.fix ?: return emptyList()
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
            return emptyList()
        return nearby
            .asSequence()
            // A classified camera produces only a nearby-reference advisory, never
            // an assertion of the current lane's speed limit or active enforcement.
            .filter {
                (it.kind == RoadSafetyKind.BUMP || it.cameraAdvisory != CameraAdvisory.NONE) &&
                    now in it.validFromMs until it.expiresAtMs
            }
            .mapNotNull { item ->
                val match =
                    NavigationGeometry.match(
                        route,
                        item.point,
                        previous = g.progressMeters + 300,
                        maxDelta = 350.0,
                        continuityWeight = 0.0,
                        maxGap = 12.0,
                        ambiguityRadius = 15.0,
                    )
                val ahead = match.progressMeters - g.progressMeters
                val maximum = if (item.kind == RoadSafetyKind.CAMERA) 500.0 else 250.0
                if (match.gapMeters > 12 || match.directionUncertain || ahead !in 40.0..maximum)
                    null
                else RoadSafetyApproach(item, ahead)
            }
            .sortedBy { it.meters }
            .toList()
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
