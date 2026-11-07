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
    @Test fun signalValidationAndExpiryContinueWhileTheCallerIsBlocked() {
        val scope=newVehicleSignalScope()
        val time=java.util.concurrent.atomic.AtomicLong(10_000)
        val connected=java.util.concurrent.CountDownLatch(1)
        val expired=java.util.concurrent.CountDownLatch(1)
        val transport=FakeTransport()
        val provider=VehicleStateProvider(true,{transport},scope,{time.get()},{time.get()*1_000_000},
            reportStatus={reason ->
                if(reason.contains("AAOS")) connected.countDown()
                if(reason.contains("만료")) expired.countDown()
            })
        try {
            provider.start();transport.park()
            // The caller cannot dispatch UI work while blocked on these latches.
            assertTrue(connected.await(2,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(provider.samples.value.connected)
            time.addAndGet(3001)
            assertTrue(expired.await(2,java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(provider.samples.value.connected)
            assertEquals(Gear.UNKNOWN,provider.samples.value.gear)
        } finally { provider.stop();scope.cancel() }
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
}
