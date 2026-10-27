package io.cabinos.one.platform

import org.junit.Assert.*
import org.junit.Test

class GuidanceRepeatGuardTest {
    @Test fun rejectedSpeechCanRetryWithoutWaitingThirtySeconds() {
        val guard=GuidanceRepeatGuard()
        val g=io.cabinos.core.Guidance(turnMeters=200.0,nextInstruction="우회전입니다.")
        assertTrue(guard.allow(g,0))
        // No accepted() call: the engine rejected the first attempt.
        assertTrue(guard.allow(g,1000))
        guard.accepted(g)
        assertFalse(guard.allow(g,2000))
        assertTrue(guard.allow(g.copy(turnMeters=30.0),3000))
    }

    @Test fun oscillatingDistanceBandsCannotRepeatContinuously() {
        val guard=GuidanceRepeatGuard()
        assertTrue(guard.allow("left|soon",0));assertTrue(guard.allow("left|now",1000))
        repeat(10){assertFalse(guard.allow("left|soon",2000L+it))}
        assertTrue(guard.allow("right|soon",3000))
        assertTrue(guard.allow("left|soon",30000))
    }
    @Test fun explicitRepeatClearsSuppression() {
        val guard=GuidanceRepeatGuard();assertTrue(guard.allow("left",0))
        guard.clear();assertTrue(guard.allow("left",1))
    }
}
