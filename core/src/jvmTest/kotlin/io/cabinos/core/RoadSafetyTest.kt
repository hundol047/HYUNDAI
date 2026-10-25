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

    @Test
    fun cameraDistanceAndSectionLabelsAreReferenceAdvisories() {
        val camera =
            bump.copy(
                id = "camera",
                point = NavPoint(36.704, 126.5),
                kind = RoadSafetyKind.CAMERA,
                cameraAdvisory = CameraAdvisory.SECTION_START,
                reportedSpeedKmh = 60,
            )
        val index = RoadSafetyIndex(listOf(camera))
        val item = index.upcoming(guidance, now).single()
        assertEquals(450, item.roundedMeters)
        assertTrue(item.spoken(true).contains("450미터"))
        assertTrue(item.spoken(true).contains("구간단속 시작 지점"))
        assertTrue(item.spoken(true).contains("경로 주변"))
        assertFalse(item.spoken(true).contains("60"))
        assertTrue(
            item
                .copy(item = camera.copy(cameraAdvisory = CameraAdvisory.SECTION_END))
                .spoken(true)
                .contains("구간단속 종료 지점")
        )
        assertTrue(index.upcoming(guidance.copy(directionUncertain = true), now).isEmpty())
        assertTrue(index.upcoming(guidance.copy(offRoute = true), now).isEmpty())
        assertTrue(index.upcoming(guidance, now + 10_001).isEmpty())
        assertTrue(index.upcoming(guidance.copy(progressMeters = 500.0), now).isEmpty())
    }

    @Test
    fun cameraAndBumpHaveDifferentApproachWindows() {
        val far = bump.copy(point = NavPoint(36.704, 126.5))
        assertTrue(RoadSafetyIndex(listOf(far)).upcoming(guidance, now).isEmpty())
        val camera = far.copy(kind = RoadSafetyKind.CAMERA, cameraAdvisory = CameraAdvisory.FIXED)
        assertEquals(1, RoadSafetyIndex(listOf(camera)).upcoming(guidance, now).size)
        assertTrue(
            RoadSafetyIndex(listOf(camera.copy(point = NavPoint(36.705, 126.5))))
                .upcoming(guidance, now)
                .isEmpty()
        )
    }

    @Test
    fun displayPinLimitAndAlreadySpokenPointDoNotHideNextAdvisory() {
        val clutter = (1..40).map { bump.copy(id = "near$it", point = NavPoint(36.7001, 126.5002)) }
        val next = bump.copy(id = "next", point = NavPoint(36.702, 126.5))
        val index = RoadSafetyIndex(clutter + bump + next)
        assertEquals(32, index.nearby(fix, now).size)
        val upcoming = index.upcoming(guidance, now)
        assertEquals(listOf("test", "next"), upcoming.map { it.item.id })
        val guard = RoadSafetyAnnouncements()
        guard.accepted(upcoming.first(), now)
        assertEquals("next", upcoming.first { guard.eligible(it, now + 15_000, 900.0) }.item.id)
    }
}
