package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class IntelligenceTest {
    private val now=1_000_000L
    private fun hazard(id:String="a", route:String="demo-pangyo", meters:Double=1500.0, confidence:Double=.9,
                       reported:Long=now-1000, expires:Long=now+60000, heading:Double=0.0, severity:Double=.8)=
        RoadHazard(id,"위험 신고",route,meters,heading,severity,confidence,3,reported,expires)
    @Test fun deduplicatesByEventIdentity() { assertEquals(1,HazardRanker().rank(listOf(hazard(),hazard()),Route(),now).size) }
    @Test fun filtersExpiredAndFutureEvents() { assertTrue(HazardRanker().rank(listOf(hazard(expires=now),hazard(reported=now+1)),Route(),now).isEmpty()) }
    @Test fun filtersWrongDirectionRouteAndBehindVehicle() { assertTrue(HazardRanker().rank(listOf(hazard(route="wrong"),hazard(heading=180.0),hazard(meters=-1.0)),Route(),now).isEmpty()) }
    @Test fun lowConfidenceIsExcluded() { assertTrue(HazardRanker().rank(listOf(hazard(confidence=.1)),Route(),now).isEmpty()) }
    @Test fun severeFreshCloseReportsRankHigher() { val result=HazardRanker().rank(listOf(hazard("weak",severity=.2,meters=9000.0),hazard("strong")),Route(),now);assertEquals("strong",result.first().hazard.id) }
    @Test fun cooldownAndDuplicateVoiceSuppression() { val gate=HazardAlertGate();val h=RankedHazard(hazard(),1500.0,.9);assertTrue(gate.take(h,now));assertFalse(gate.take(h,now+1000));assertFalse(gate.take(h,now+60001));assertTrue(gate.take(h.copy(hazard=hazard("b")),now+60001)) }
    @Test fun farHazardsDoNotInterrupt() { assertFalse(HazardAlertGate().take(RankedHazard(hazard(),4000.0,.9),now)) }
    @Test fun waitingBudgetAccountsForReturnAndBuffer() { assertEquals(1920L,TimeOptimizer.calculate(now+2400000,now,300,180).availableSeconds) }
    @Test fun missingOrExpiredDepartureClampsToZero() { assertEquals(0L,TimeOptimizer.calculate(null,now,300).availableSeconds);assertEquals(0L,TimeOptimizer.calculate(now-1,now,300).availableSeconds) }
    @Test fun recommendationsUseRoundTripAndRespectCategory() { val s=TimeOptimizer.calculate(now+1200000,now,300);val result=TimeOptimizer.recommend(DemoActivityRepository().activities(),s,"카페");assertEquals(4,result.size);assertTrue(result.all {it.activity.category=="카페"});assertEquals(listOf(540L,300L,60L,0L),result.map {it.usableSeconds});assertFalse(result.first().feasible) }
}
