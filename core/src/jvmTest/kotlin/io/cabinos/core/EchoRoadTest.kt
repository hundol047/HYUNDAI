package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class EchoRoadTest {
    private fun fix(north:Double,speed:Double,time:Long,accuracy:Double=5.0)=NavFix(NavPoint(37.0+north/111195,127.0),accuracy,time,speed,0.0)
    @Test fun sustainedDecelerationProducesOneCandidateAndCooldown() {
        val d=BrakeDetector()
        assertNull(d.accept(fix(0.0,20.0,10000),10000))
        assertNull(d.accept(fix(18.0,16.0,11000),11000))
        assertNotNull(d.accept(fix(32.0,12.0,12000),12000))
        assertNull(d.accept(fix(42.0,8.0,13000),13000))
        assertNull(d.accept(fix(48.0,4.0,14000),14000))
    }
    @Test fun singleSpeedSpikeStaleGpsAndPositionJumpDoNotBecomeReports() {
        val d=BrakeDetector()
        d.accept(fix(0.0,20.0,10000),10000)
        assertNull(d.accept(fix(18.0,16.0,11000),11000))
        assertNull(d.accept(fix(32.0,12.0,12000,40.0),12000))
        assertNull(d.accept(fix(42.0,8.0,13000),16000))
        d.reset();d.accept(fix(0.0,20.0,20000),20000)
        assertNull(d.accept(fix(200.0,16.0,21000),21000))
        assertNull(d.accept(fix(214.0,12.0,22000),22000))
    }
    @Test fun changedHeadingResetsCandidate() {
        val d=BrakeDetector();d.accept(fix(0.0,20.0,10000),10000)
        d.accept(fix(18.0,16.0,11000),11000)
        assertNull(d.accept(fix(32.0,12.0,12000).copy(bearingDegrees=100.0),12000))
    }
    @Test fun onlyFreshForwardSameDirectionRisksAreMatched() {
        val start=NavPoint(36.7,126.5);val end=NavPoint(36.71,126.5);val now=1000000L
        val route=NavigationGeometry.route(NavPlace("검사 목적지",end),listOf(start,end),1112.0,150.0,listOf(NavManeuver(start,"depart"),NavManeuver(end,"arrive")))
        val guidance=Guidance(route=route,active=true,tracking=true,fix=NavFix(start,5.0,now,8.0,0.0),turnMeters=900.0)
        val risk=EchoRisk("test",NavPoint(36.701,126.5),0.0,now+60000,"합성 검사 제보","test",2)
        assertNotNull(EchoRoadMatch.ahead(guidance,listOf(risk),now))
        assertNull(EchoRoadMatch.ahead(guidance,listOf(risk.copy(heading=180.0)),now))
        assertNull(EchoRoadMatch.ahead(guidance,listOf(risk.copy(expiresAtMs=now)),now))
        assertNull(EchoRoadMatch.ahead(guidance.copy(progressMeters=200.0),listOf(risk),now))
        assertNull(EchoRoadMatch.ahead(guidance.copy(tracking=false),listOf(risk),now))
    }
}
