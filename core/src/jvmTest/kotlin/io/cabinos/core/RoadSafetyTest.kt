package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class RoadSafetyTest {
    private val now = 1_000_000L
    private val start = NavPoint(36.7, 126.5)
    private val end = NavPoint(36.71, 126.5)
    private val route =
        NavigationGeometry.route(
            NavPlace("합성 검사 목적지", end),
            listOf(start, end),
            1112.0,
            150.0,
            listOf(NavManeuver(start, "depart"), NavManeuver(end, "arrive")),
        )
    private val bump =
        RoadSafetyPoint(
            "test",
            NavPoint(36.701, 126.5),
            RoadSafetyKind.BUMP,
            "검사도로",
            "2026-01-01",
            now + 100_000,
        )
    private val fix = NavFix(start, 5.0, now, 8.0, 0.0)
    private val guidance =
        Guidance(route = route, active = true, tracking = true, fix = fix, turnMeters = 900.0)

    @Test
    fun findsForwardBumpButNotParallelOrPassedPoints() {
        val index = RoadSafetyIndex(listOf(bump))
        assertTrue(index.approach(guidance, index.nearby(fix, now), now)!!.meters in 100.0..120.0)
        assertNull(index.approach(guidance.copy(progressMeters = 200.0), listOf(bump), now))
        assertNull(
            index.approach(guidance, listOf(bump.copy(point = NavPoint(36.701, 126.501))), now)
        )
    }

    @Test
    fun stalePoorOrUncertainGpsSuppressesReferencesAndWarnings() {
        val index = RoadSafetyIndex(listOf(bump))
        assertEquals(
            emptyList<RoadSafetyPoint>(),
            index.nearby(fix.copy(timestampMs = now - 10_001), now),
        )
        assertEquals(emptyList<RoadSafetyPoint>(), index.nearby(fix.copy(accuracy = 40.0), now))
        assertNull(index.approach(guidance.copy(directionUncertain = true), listOf(bump), now))
        assertNull(index.approach(guidance.copy(offRoute = true), listOf(bump), now))
        assertNull(
            index.approach(guidance.copy(fix = fix.copy(speedMps = null)), listOf(bump), now)
        )
    }

    @Test
    fun expiredDataAndDirectionUnknownCameraNeverBecomeVoiceWarnings() {
        val expired = bump.copy(expiresAtMs = now)
        assertTrue(
            RoadSafetyIndex(listOf(bump.copy(validFromMs = now + 1))).nearby(fix, now).isEmpty()
        )
        val camera = bump.copy(kind = RoadSafetyKind.CAMERA, reportedSpeedKmh = 30)
        val index = RoadSafetyIndex(listOf(expired, camera))
        assertEquals(listOf(camera), index.nearby(fix, now))
        assertNull(index.approach(guidance, listOf(camera, expired), now))
    }

    @Test
    fun announcementsDoNotRepeatAndNeverCompeteWithImmediateTurn() {
        val guard = RoadSafetyAnnouncements()
        val item = RoadSafetyApproach(bump, 100.0)
        assertFalse(guard.eligible(item, now, 100.0))
        assertTrue(guard.eligible(item, now, 500.0))
        guard.accepted(item, now)
        assertFalse(guard.eligible(item, now + 60_000, 500.0))
        val other = item.copy(item = bump.copy(id = "other"))
        assertFalse(guard.eligible(other, now + 14_999, 500.0))
        guard.reset()
        assertTrue(guard.eligible(item, now + 60_000, 500.0))
    }
}
