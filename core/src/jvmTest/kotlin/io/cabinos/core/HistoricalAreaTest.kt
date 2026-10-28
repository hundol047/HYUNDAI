package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class HistoricalAreaTest {
    private fun box(s:Double,w:Double,n:Double,e:Double)=listOf(NavPoint(s,w),NavPoint(s,e),NavPoint(n,e),NavPoint(n,w),NavPoint(s,w))
    private val route=NavigationGeometry.route(NavPlace("합성 경로",NavPoint(37.01,127.0)),listOf(NavPoint(37.0,127.0),NavPoint(37.01,127.0)),1112.0,150.0,listOf(NavManeuver(NavPoint(37.0,127.0),"depart"),NavManeuver(NavPoint(37.01,127.0),"arrive")))
    @Test fun boundaryEntryWorksEvenWhenCenterIsFarFromRoad() {
        val area=HistoricalArea(listOf(listOf(box(37.001,126.999,37.003,127.003))))
        assertTrue(area.ahead(route,0.0)!! in 110.0..113.0)
        assertEquals(0.0,area.ahead(route,220.0)!!,0.1)
        assertNull(area.ahead(route,500.0))
        assertNull(HistoricalArea(listOf(listOf(box(37.001,127.001,37.003,127.003)))).ahead(route,0.0))
    }
    @Test fun holesAndMultipleComponentsAreNotFilledIn() {
        val area=HistoricalArea(listOf(listOf(box(37.001,126.999,37.005,127.001),box(37.002,126.9995,37.004,127.0005)),listOf(box(37.006,126.999,37.007,127.001))))
        assertTrue(area.valid());assertFalse(area.contains(NavPoint(37.003,127.0)))
        assertTrue(area.ahead(route,333.0)!! in 110.0..114.0)
        assertTrue(area.contains(NavPoint(37.0065,127.0)))
    }
    @Test fun staleOrOffRouteGuidanceNeverAnnouncesHistoricalAreas() {
        val area=HistoricalArea(listOf(listOf(box(37.001,126.999,37.003,127.003))))
        val risk=EchoRisk("area",NavPoint(37.002,127.002),null,1060000,"과거 사고 통계","official",area=area)
        val g=Guidance(route=route,active=true,tracking=true,fix=NavFix(route.points.first(),5.0,1000000,10.0,0.0))
        assertNotNull(EchoRoadMatch.ahead(g,listOf(risk),1000000))
        assertNull(EchoRoadMatch.ahead(g.copy(offRoute=true),listOf(risk),1000000))
        assertNull(EchoRoadMatch.ahead(g,listOf(risk),1004000))
    }
}
