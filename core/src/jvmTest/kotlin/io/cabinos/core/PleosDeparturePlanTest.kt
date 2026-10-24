package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class PleosDeparturePlanTest {
    private var now=1_000_000L
    private val engine=UnifiedVehicleContextEngine({now})
    private fun sample(soc:Int=68, pleos:Boolean=true, charging:Boolean=true, seconds:Long?=2400, gear:Gear=Gear.PARK) {
        engine.acceptVehicle(VehicleSample(gear=gear,parkingReliable=true,charging=charging,batteryPercent=soc,
            chargeRemainingSeconds=seconds,timestampMs=now,source=DataSource.VEHICLE,pleosSoc=pleos))
    }
    private fun park() {sample();now+=1600;sample()}
    @Test fun sdkSocTargetEndsFocusAndNearbyBudgetWithoutInventingChargeEta() {
        park();assertTrue(engine.setChargeTarget(80));assertTrue(engine.startSession(SessionKind.FOCUS,25))
        sample(79);assertEquals(SessionStatus.RUNNING,engine.state.value.session!!.status)
        sample(80)
        assertEquals(SessionStatus.COMPLETED,engine.state.value.session!!.status)
        assertEquals(0L,engine.state.value.schedule.availableSeconds)
        assertEquals("Pleos SOC 목표 도달",engine.state.value.schedule.estimateLabel)
        assertEquals(VehicleContext.PREPARING_DEPARTURE,engine.state.value.context)
        sample(79,pleos=false);assertEquals(0L,engine.state.value.schedule.availableSeconds)
    }
    @Test fun aaosSocAloneCannotClaimPleosTargetReached() {
        park();engine.setChargeTarget(80);sample(90,pleos=false)
        assertEquals("차량 충전 예상",engine.state.value.schedule.estimateLabel)
        assertTrue(engine.state.value.schedule.availableSeconds>0)
    }
    @Test fun manualWaitingCannotExtendAnEarlierChargeDeadline() {
        park();sample(seconds=900);val deadline=engine.state.value.schedule.departureAtMs
        engine.setWaitingMinutes(120);assertEquals(deadline,engine.state.value.schedule.departureAtMs)
    }
    @Test fun destinationSecondsAndMetersConstrainSharedFocusBudget() {
        park();engine.setArrivalInMinutes(60)
        engine.updateNavigationEstimate(2700,42000,now)
        assertEquals(now+900_000L,engine.state.value.schedule.departureAtMs)
        assertEquals(600L,engine.state.value.schedule.availableSeconds)
        assertTrue(engine.startSession(SessionKind.FOCUS,25))
        assertEquals(600L,engine.state.value.session!!.durationSeconds)
        engine.updateNavigationEstimate(3000,42000,now)
        assertEquals(300L,engine.state.value.session!!.remainingSeconds)
    }
    @Test fun missingOrFasterRouteDoesNotSilentlyExtendFocus() {
        park();engine.setArrivalInMinutes(60);engine.updateNavigationEstimate(2700,42000,now)
        val deadline=engine.state.value.schedule.departureAtMs
        engine.updateNavigationEstimate(1800,30000,now);assertEquals(deadline,engine.state.value.schedule.departureAtMs)
        engine.updateNavigationEstimate(null,null,null);assertEquals(deadline,engine.state.value.schedule.departureAtMs)
        assertTrue(engine.state.value.schedule.estimateLabel.contains("만료"))
        engine.setArrivalInMinutes(null);assertEquals("차량 충전 예상",engine.state.value.schedule.estimateLabel)
    }
    @Test fun noArrivalIntentOrInvalidSdkDataCannotCreateADeparture() {
        park();sample(charging=false,seconds=null)
        engine.updateNavigationEstimate(1200,10000,now);assertNull(engine.state.value.schedule.departureAtMs)
        engine.setArrivalInMinutes(60)
        engine.updateNavigationEstimate(-1,10000,now);assertNull(engine.state.value.schedule.departureAtMs)
        engine.updateNavigationEstimate(1200,-1,now);assertNull(engine.state.value.schedule.departureAtMs)
        engine.updateNavigationEstimate(1200,10000,now-10001);assertNull(engine.state.value.schedule.departureAtMs)
    }
    @Test fun sdkEnergyAndArrivalCannotUnlockTrafficStop() {
        park();engine.setChargeTarget(80);engine.setArrivalInMinutes(60)
        sample(80,gear=Gear.DRIVE);engine.updateNavigationEstimate(1200,10000,now)
        assertFalse(engine.state.value.parkedAllowed)
        assertFalse(engine.startSession(SessionKind.FOCUS,10))
        assertFalse(engine.setChargeTarget(90));assertFalse(engine.setArrivalInMinutes(30))
    }
}
