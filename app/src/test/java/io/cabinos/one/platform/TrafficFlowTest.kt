package io.cabinos.one.platform

import io.cabinos.core.NavPoint
import org.junit.Assert.*
import org.junit.Test

class TrafficFlowTest {
    private fun flow(speed:Double,confidence:Double=1.0,closed:Boolean=false)=TrafficFlow(listOf(NavPoint(52.3,4.9),NavPoint(52.31,4.91)),speed,50.0,confidence,closed,100000)
    @Test fun congestionLevelsUseRelativeSpeedAndUncertaintyNeverGreen() {
        assertEquals("정체",flow(20.0).level);assertEquals("서행",flow(35.0).level);assertEquals("원활",flow(50.0).level)
        assertEquals("정보 불확실",flow(50.0,.4).level);assertEquals("통제",flow(50.0,closed=true).level)
    }
    @Test fun expiredAndFutureFlowsAreRejected() {
        assertTrue(flow(20.0).current(100000));assertFalse(flow(20.0).current(99999));assertFalse(flow(20.0).current(190001))
    }
}
