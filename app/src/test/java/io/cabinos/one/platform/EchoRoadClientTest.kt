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
class EchoRoadClientTest {
    private val point=NavPoint(37.5,127.0)
    private val response="""{"generatedAtMs":1000000,"accidentDatasetConnected":false,"accidentZones":[],"risks":[{"id":"r","kind":"repeated_braking","reporters":2,"lat":37.5,"lon":127.0,"heading":0,"observedAtMs":999000,"expiresAtMs":1899000}]}"""
    @Test fun freshFeedHasBoundedClientLifetimeAndDoesNotClaimAccidents() {
        val (risks,official)=EchoRoadClient.parse(response,point,1000000)
        assertFalse(official);assertEquals(1090000L,risks.single().expiresAtMs)
        assertEquals("반복 급감속 제보",risks.single().label)
    }
    @Test fun staleFeedAndSingleReporterCannotWarn() {
        assertThrows(IllegalArgumentException::class.java){EchoRoadClient.parse(response,point,1100000)}
        val bad=JSONObject(response);bad.getJSONArray("risks").getJSONObject(0).put("reporters",1)
        assertThrows(IllegalArgumentException::class.java){EchoRoadClient.parse(bad.toString(),point,1000000)}
    }
    @Test fun credentialsAreNeverAllowedOverPlainHttpOrInUrl() {
        val c=EchoRoadClient()
        assertThrows(IllegalArgumentException::class.java){c.configure("http://example.org","x".repeat(24))}
        assertThrows(IllegalArgumentException::class.java){c.configure("https://name:pass@example.org","x".repeat(24))}
        c.configure("https://example.org","x".repeat(24));c.configure("","");assertEquals("",c.endpoint)
    }
}
