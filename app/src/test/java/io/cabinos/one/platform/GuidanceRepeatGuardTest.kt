package io.cabinos.one.platform

import org.junit.Assert.*
import org.junit.Test

class GuidanceRepeatGuardTest {
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
