package io.cabinos.one

import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.platform.RoadSafetyRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = CabinApplication::class)
class RoadSafetyRepositoryTest {
    @Test
    fun bundledOfficialSnapshotIsValidatedAndHasNoDuplicateCoordinates() {
        val points = RoadSafetyRepository.load(ApplicationProvider.getApplicationContext())
        assertEquals(9314, points.size)
        assertEquals(points.size, points.map { it.point }.distinct().size)
        assertTrue(
            points.all {
                it.kind == RoadSafetyKind.BUMP && it.point.valid() && it.reportedSpeedKmh == null
            }
        )
        assertTrue(points.all { it.expiresAtMs > it.validFromMs && it.recordDate == "2025-11-13" })
    }

    @Test
    fun projectedCoordinatesAreNotTreatedAsLatitudeLongitude() {
        val json =
            """{"schema":1,"records":[["test",200000,450000,"bump","검사도로","2025-11-13",null]]}"""
        try {
            RoadSafetyRepository.parse(json)
            fail("Projected coordinates accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
