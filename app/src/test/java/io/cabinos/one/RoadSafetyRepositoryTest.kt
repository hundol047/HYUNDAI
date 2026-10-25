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
        val bumps = points.filter { it.kind == RoadSafetyKind.BUMP }
        val cameras = points.filter { it.kind == RoadSafetyKind.CAMERA }
        assertEquals(82697, bumps.size)
        assertEquals(39827, cameras.size)
        assertEquals(122524, points.size)
        assertEquals(points.size, points.map { it.kind to it.point }.distinct().size)
        val checkedAt =
            java.time.LocalDate.of(2026, 10, 25)
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        assertEquals(39384, bumps.count { checkedAt in it.validFromMs until it.expiresAtMs })
        assertEquals(37267, cameras.count { checkedAt in it.validFromMs until it.expiresAtMs })
        assertTrue(points.all { it.point.valid() && it.expiresAtMs > it.validFromMs })
        assertTrue(
            bumps.all { it.reportedSpeedKmh == null && it.recordDate in "2019-01-01".."2026-08-27" }
        )
        assertTrue(cameras.all { it.reportedSpeedKmh == null || it.reportedSpeedKmh in 10..140 })
        assertTrue(cameras.all { it.recordDate in "2021-09-24".."2026-09-13" })
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

    @Test
    fun streamingParserAcceptsMetadataAfterRowsAndRejectsUnsupportedSchema() {
        val rows = """[["fixture",37.5,127.0,"bump","검사도로","2026-01-01",null]]"""
        val points =
            RoadSafetyRepository.parse(
                """{"records":$rows,"schema":1,"coverage":"검사용","asOf":"2026-10-25"}"""
            )
        assertEquals("2026-01-01", points.single().recordDate)
        assertThrows(IllegalArgumentException::class.java) {
            RoadSafetyRepository.parse("""{"schema":99,"records":$rows}""")
        }
    }

    @Test
    fun schemaTwoRetainsOnlyRecognizedCameraAdvisories() {
        val points =
            RoadSafetyRepository.parse(
                """{"schema":2,"records":[
          ["start",37.5,127.0,"camera","도로","2026-01-01",60,"section_start"],
          ["unknown",37.6,127.0,"camera","도로","2026-01-01",60,"unrecognized"]
        ]}"""
            )
        assertEquals(CameraAdvisory.SECTION_START, points[0].cameraAdvisory)
        assertEquals(CameraAdvisory.NONE, points[1].cameraAdvisory)
    }
}
