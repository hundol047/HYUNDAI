package io.cabinos.one.platform

import io.cabinos.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VehicleStateProviderTest {
    private class FakeTransport : VehiclePropertyTransport {
        lateinit var listener: VehiclePropertyTransport.Listener
        var stopped=false
        override fun start(listener:VehiclePropertyTransport.Listener){this.listener=listener}
        override fun stop(){stopped=true}
        fun park(){listener.connected();listener.speed(0f,10_000_000_000);listener.gear(4,10_000_000_000)}
    }
    @Test fun phoneNeverLoadsCarLibraryAndDoesNotFallBackToSimulation() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val provider=VehicleStateProvider(false,{error("must not load Car API")},scope,{10_000},{10_000_000_000})
            provider.start();assertFalse(provider.samples.value.connected)
            assertEquals(DataSource.VEHICLE,provider.samples.value.source)
            assertTrue(provider.status.value.contains("차량 서비스 없음"));provider.stop()
        } finally {scope.cancel()}
    }
    @Test fun stopRejectsLateCallbacksAndReconnectRequiresNewSignals() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val transports=mutableListOf<FakeTransport>()
        try {
            val provider=VehicleStateProvider(true,{FakeTransport().also{transports+=it}},scope,{10_000},{10_000_000_000})
            provider.start();transports[0].park();assertTrue(provider.samples.value.connected)
            provider.stop();assertTrue(transports[0].stopped)
            transports[0].park();assertFalse(provider.samples.value.connected)
            provider.start();transports[0].park();assertFalse(provider.samples.value.connected)
            transports[1].park();assertTrue(provider.samples.value.connected)
            transports[1].listener.disconnected("service died");assertFalse(provider.samples.value.connected)
            transports[1].listener.speed(0f,10_000_000_000);assertFalse(provider.samples.value.connected)
            provider.stop()
        } finally {scope.cancel()}
    }
    @Test fun permissionOrLibraryFailureStaysUnknown() {
        for(error in listOf(SecurityException("denied"),NoClassDefFoundError("android.car.Car"))) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            try {
                val provider=VehicleStateProvider(true,{throw error},scope,{10_000},{10_000_000_000})
                provider.start();assertFalse(provider.samples.value.connected);assertEquals(Gear.UNKNOWN,provider.samples.value.gear)
                provider.stop()
            } finally {scope.cancel()}
        }
    }
    @Test fun diagnosticTimingIsThrottledAndCannotRefreshOldSpeed() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        var time=10_000_000_000L
        val transport=FakeTransport();val logs=mutableListOf<String>()
        try {
            val provider=VehicleStateProvider(true,{transport},scope,{time/1_000_000},{time},{logs+=it})
            provider.start();transport.park()
            repeat(20) { transport.listener.timing(time,time);transport.listener.speed(0f,10_000_000_000) }
            assertEquals(1,logs.size)
            time+=4_000_000_000
            transport.listener.timing(time,time);transport.listener.speed(0f,10_000_000_000);transport.listener.gear(4,10_000_000_000)
            assertEquals(4000L,provider.diagnostics.value.speedAgeMs)
            assertEquals(14000L,provider.diagnostics.value.lastPollSuccessMs)
            assertEquals(2,logs.size);assertTrue(logs.last().contains("speedAgeMs=4000"))
            assertFalse(provider.samples.value.connected)
            provider.stop();transport.listener.timing(time,time)
            assertNull(provider.diagnostics.value.lastPollSuccessMs)
            assertNull(provider.diagnostics.value.speedAgeMs)
        } finally {scope.cancel()}
    }
}
