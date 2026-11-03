package io.cabinos.one.platform

import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test

class VehicleReceiptFreshnessTest {
    private var now=10_000_000_000L
    private fun gate(enabled:Boolean)=VehicleSignalGate({now/1_000_000},{now},enabled).apply { connected() }
    private fun poll(gate:VehicleSignalGate,speed:Float=0f,gear:Int=4,subscribed:Boolean=true,connected:Boolean=true) {
        gate.speed(speed,10_000_000_000);gate.gear(gear,10_000_000_000)
        gate.receiptPoll(speed,gear,now,subscribed,connected)
    }
    @Test fun frozenTimestampOnlyUnlocksWithExplicitFlagAndDebounce() {
        for(enabled in listOf(false,true)) {
            now=14_000_000_000
            val gate=gate(enabled)
            val engine=UnifiedVehicleContextEngine({now/1_000_000},DemoRoadHazardRepository())
            poll(gate);engine.acceptVehicle(gate.sample());assertFalse(engine.state.value.parkedAllowed)
            now+=1_600_000_000;poll(gate);engine.acceptVehicle(gate.sample())
            assertEquals(enabled,engine.state.value.parkedAllowed)
        }
    }
    @Test fun movingOrDriveGearNeverRelaxesTimestamp() {
        val gate=gate(true);now+=4_000_000_000
        poll(gate,speed=0.01f);assertFalse(gate.sample().connected)
        poll(gate,gear=8);assertFalse(gate.sample().connected)
    }
    @Test fun pollFailureOrErrorDisablesReceiptUntilReconnect() {
        val gate=gate(true);now+=4_000_000_000;poll(gate);assertTrue(gate.sample().connected)
        gate.unavailable("poll failed");poll(gate);assertFalse(gate.sample().connected)
        gate.connected();poll(gate);assertTrue(gate.sample().connected)
        gate.reset("disconnected");poll(gate);assertFalse(gate.sample().connected)
    }
    @Test fun subscriptionConnectionAndSubsecondPollAreRequired() {
        val gate=gate(true);now+=4_000_000_000
        poll(gate,subscribed=false);assertFalse(gate.sample().connected)
        poll(gate,connected=false);assertFalse(gate.sample().connected)
        poll(gate);now+=1_000_000_000;assertFalse(gate.sample().connected)
    }
}
