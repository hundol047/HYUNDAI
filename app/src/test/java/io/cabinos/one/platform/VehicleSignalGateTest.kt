package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic transport tests, not evidence of a Pleos emulator run. */
class VehicleSignalGateTest {
    private var time = 10_000L
    private fun ns() = time * 1_000_000
    private val gate = VehicleSignalGate({ time }, ::ns)
    private val engine = UnifiedVehicleContextEngine(clock = { time })
    private fun update(speed: Float = 0f, gear: Int = 4) {
        gate.speed(speed, ns()); gate.gear(gear, ns()); engine.acceptVehicle(gate.sample())
    }
    @Test fun requiresBothRealPropertiesAndDoesNotInventBatteryOrCharging() {
        gate.connected(); gate.gear(4, ns())
        assertFalse(gate.sample().connected)
        gate.speed(0f, ns())
        val value = gate.sample()
        assertTrue(value.connected); assertEquals(DataSource.VEHICLE, value.source)
        assertEquals(-1, value.batteryPercent); assertFalse(value.charging); assertNull(value.chargeRemainingSeconds)
    }
    @Test fun convertsMetersPerSecondIncludingReverseWithoutSuppressingSmallMotion() {
        gate.connected(); update(10f, 8); assertEquals(36.0, gate.sample().speedKph, .00001)
        update(-2f, 2); assertEquals(7.2, gate.sample().speedKph, .00001)
        update(.01f, 4); assertEquals(VehicleContext.DRIVING, engine.state.value.context)
    }
    @Test fun parkRequiresDebounceAndDriveAtZeroIsTrafficNotParking() {
        gate.connected(); update(); assertFalse(engine.state.value.parkedAllowed)
        time += 1600; update(); assertTrue(engine.state.value.parkedAllowed)
        update(0f, 8); assertFalse(engine.state.value.parkedAllowed)
        assertEquals(VehicleContext.STOPPED_IN_TRAFFIC, engine.state.value.context)
        update(0f, 2); assertFalse(engine.state.value.parkedAllowed)
        update(0f, 1); assertEquals(VehicleContext.UNKNOWN, engine.state.value.context)
    }
    @Test fun manualForwardGearsAreNeverParkAndUnknownEnumsFailClosed() {
        gate.connected()
        for (value in listOf(8,16,32,64,128,256,512,1024,2048,4096)) {
            update(0f,value); assertEquals(Gear.DRIVE,gate.sample().gear); assertFalse(engine.state.value.parkedAllowed)
        }
        for (value in listOf(0,-1,3,12,8192)) { update(0f,value); assertFalse(gate.sample().connected) }
    }
    @Test fun repeatedOldSpeedReadsDoNotRenewFreshness() {
        gate.connected(); update(); val original=ns()
        time+=1600; update(); assertTrue(engine.state.value.parkedAllowed)
        time+=4000; gate.speed(0f,original); gate.gear(4,original)
        engine.acceptVehicle(gate.sample()); assertFalse(engine.state.value.parkedAllowed)
        assertFalse(gate.sample().connected)
    }
    @Test fun oldContinuousSpeedExpiresButUnchangedGearMayBeConfirmedByRead() {
        gate.connected(); val original=ns(); update()
        time+=20_000; gate.speed(0f,ns()); gate.gear(4,original)
        assertTrue(gate.sample().connected) // ON_CHANGE timestamp legitimately remains old.
        time+=3001; assertFalse(gate.sample().connected)
    }
    @Test fun oldGearWithoutCurrentReadExpiresEvenWithFreshSpeed() {
        gate.connected(); update(); time+=3001; gate.speed(0f,ns())
        assertFalse(gate.sample().connected)
    }
    @Test fun invalidUnavailableAndFutureValuesRevokeParking() {
        for (value in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,201f)) {
            gate.connected();update();gate.speed(value,ns());assertFalse(gate.sample().connected)
        }
        gate.connected();update();gate.speed(0f,ns()+1);assertFalse(gate.sample().connected)
        gate.connected();update();gate.gear(4,0);assertFalse(gate.sample().connected)
        gate.connected();update();gate.unavailable("permission denied");assertFalse(gate.sample().connected)
    }
    @Test fun reversedGearEventsInvalidateBothSignalsAndNeedFreshPair() {
        gate.connected();update();time+=100;update(0f,8)
        gate.gear(4,ns()-1);assertFalse(gate.sample().connected)
        gate.gear(8,ns());assertFalse(gate.sample().connected)
        gate.speed(0f,ns());assertTrue(gate.sample().connected);assertEquals(Gear.DRIVE,gate.sample().gear)
    }
    @Test fun disconnectCannotRestoreCachedParkAndInterruptsActiveSession() {
        gate.connected();update();time+=1600;update()
        engine.setWaitingMinutes(40);engine.startSession(SessionKind.FOCUS,25)
        assertEquals(SessionStatus.RUNNING,engine.state.value.session?.status)
        gate.reset("disconnected");engine.acceptVehicle(gate.sample())
        assertFalse(engine.state.value.parkedAllowed)
        assertNotEquals(SessionStatus.RUNNING,engine.state.value.session?.status)
        update();assertFalse(gate.sample().connected)
        gate.connected();gate.speed(0f,ns());assertFalse(gate.sample().connected)
        gate.gear(4,ns());engine.acceptVehicle(gate.sample());assertFalse(engine.state.value.parkedAllowed)
    }
}
