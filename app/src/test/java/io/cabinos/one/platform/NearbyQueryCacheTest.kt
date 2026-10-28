package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test

class NearbyQueryCacheTest {
    private val origin=MapPosition()
    @Test fun clockCorrectionCannotBlockNearbySearchForHours() {
        val cache=NearbyQueryCache();cache.begin(origin,3_600_000)
        assertEquals(0L,cache.remainingSeconds(origin,1000))
    }
    @Test fun newGpsAreaIsNotBlockedByPreviousAreaCooldown() {
        val cache=NearbyQueryCache();cache.begin(origin,1000)
        assertEquals(29L,cache.remainingSeconds(origin,2000))
        assertEquals(0L,cache.remainingSeconds(MapPosition(37.5,127.1),2000))
        cache.fail();assertEquals(0L,cache.remainingSeconds(origin,6000))
    }
    @Test fun categorySwitchAndLocationRefreshKeepRecentPlacesButNeverReuseAnotherArea() {
        val cafe=NearbyGeometry.osmActivity("1","등록 카페","카페",37.395,127.111,origin)!!
        val cache=NearbyQueryCache();cache.save(origin,listOf(cafe),1000)
        assertEquals(listOf(cafe.id),cache.read(origin,2000)?.map {it.id})
        assertNull(cache.read(MapPosition(37.5,127.1),2000))
        assertNull(cache.read(origin,121001))
        cache.clear();assertNull(cache.read(origin,2000))
    }
}
