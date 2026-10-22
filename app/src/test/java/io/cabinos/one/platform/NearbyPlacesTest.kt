package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NearbyPlacesTest {
    @Test fun overpassNodesAndWayCentersUseActualCoordinatesAndRejectBadData() {
        // Deliberately synthetic protocol fixture; not evidence of a live provider request.
        val json="""{"elements":[
          {"type":"node","id":1,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name":"Cafe","name:ko":"한국어 카페"}},
          {"type":"way","id":2,"center":{"lat":37.394,"lon":127.112},"tags":{"leisure":"park","name":"공원"}},
          {"type":"node","id":3,"lat":0,"lon":0,"tags":{"amenity":"cafe","name":"먼 곳"}},
          {"type":"node","id":4,"tags":{"amenity":"restaurant","name":"좌표 없음"}},
          {"type":"node","id":5,"lat":37.395,"lon":127.111,"tags":{"amenity":"bank","name":"제외"}},
          {"type":"node","id":1,"lat":37.395,"lon":127.111,"tags":{"amenity":"cafe","name":"중복"}}
        ]}"""
        val items=NearbyPlaces.parse(json,MapPosition())
        assertEquals(2,items.size);assertEquals("한국어 카페",items[0].name)
        assertEquals("산책",items[1].category);assertFalse(items.any {it.simulated})
        assertTrue(items.all {it.latitude!=null && it.oneWaySeconds>=60})
    }
}
