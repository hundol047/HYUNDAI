package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class WalkingServiceTest {
    private val origin=MapPosition(37.3948,127.1112)
    private val destination=MapPosition(37.3955,127.1125)
    private fun fixture()=javaClass.getResource("/foot-route-pangyo.json")!!.readText()
    @Test fun actualFootResponseUsesGeometryAndConservativeLegTimes() {
        val p=WalkingService.parse(fixture(),origin,destination,1000)
        assertEquals(240L,p.outboundSeconds);assertEquals(480L,p.roundTripSeconds)
        assertEquals(17,p.points.size);assertTrue(p.usable(1000,origin))
    }
    @Test fun largeEndpointSnapAndMissingReturnLegAreRejected() {
        val a=JSONObject(fixture());a.getJSONArray("waypoints").getJSONObject(1).put("distance",500)
        assertThrows(IllegalArgumentException::class.java){WalkingService.parse(a.toString(),origin,destination,1000)}
        val b=JSONObject(fixture());b.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").remove(1)
        assertThrows(IllegalArgumentException::class.java){WalkingService.parse(b.toString(),origin,destination,1000)}
    }
    @Test fun noRouteNeverFallsBackToStraightLine() {
        assertThrows(IllegalArgumentException::class.java){WalkingService.parse("""{"code":"NoRoute"}""",origin,destination,1000)}
    }
}
