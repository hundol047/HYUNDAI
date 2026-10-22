package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class StationaryEvidenceTest {
    private fun fix(at:Long,speed:Double?=0.0,accuracy:Double=5.0,lat:Double=37.0)=NavFix(NavPoint(lat,127.0),accuracy,at,speed)
    @Test fun onlyContinuousFreshStationaryUpdatesCount() {
        val gate=StationaryEvidence()
        for(t in 100_000L..104_000L step 1000)assertFalse(gate.accept(fix(t),t))
        assertTrue(gate.accept(fix(105_000),105_000))
        gate.reset();assertFalse(gate.accept(fix(106_000),106_000))
    }
    @Test fun gapDuplicateAndBufferedOldUpdatesCannotCompleteFiveSeconds() {
        val gate=StationaryEvidence();assertFalse(gate.accept(fix(100_000),100_000))
        assertFalse(gate.accept(fix(100_000),106_000));assertFalse(gate.accept(fix(106_000),106_000))
        for(t in 107_000L..110_000L step 1000)assertFalse(gate.accept(fix(t),t))
        assertTrue(gate.accept(fix(111_000),111_000))
        gate.reset()
        for(t in 100_000L..106_000L step 1000)assertFalse(gate.accept(fix(t),109_000))
    }
    @Test fun poorUnknownMovingAndPositionDriftResetEvidence() {
        for(bad in listOf(fix(104_000,speed=null),fix(104_000,speed=3.0),fix(104_000,accuracy=200.0),fix(104_000,lat=37.01))) {
            val gate=StationaryEvidence()
            for(t in 100_000L..103_000L step 1000)assertFalse(gate.accept(fix(t),t))
            assertFalse(gate.accept(bad,104_000));assertFalse(gate.accept(fix(105_000),105_000))
        }
    }
}
