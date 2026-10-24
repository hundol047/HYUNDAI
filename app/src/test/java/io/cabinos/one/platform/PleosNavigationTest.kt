package io.cabinos.one.platform

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PleosNavigationTest {
    private class Client: PleosNavigationClient {
        lateinit var connected: () -> Unit
        lateinit var error: (String) -> Unit
        var requests=0
        var destinationRequests=0
        lateinit var destination: (DestinationEstimate?) -> Unit
        override fun destinationListener(listener:(DestinationEstimate?)->Unit) {destination=listener}
        override fun requestDestination() {destinationRequests++}
        var released=false
        override fun initialize(connected: () -> Unit,failed: (Throwable) -> Unit,error: (String) -> Unit) {this.connected=connected;this.error=error}
        override fun open(screen: VehicleNavigationScreen,keyword: String) {requests++}
        override fun release() {released=true}
    }
    @Test fun requiresConnectionAndParkBeforeSendingAndNeverClaimsARoute() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val client=Client();val nav=PleosNavigation({client},scope)
            nav.start();assertFalse(nav.state.value.connected)
            nav.open(VehicleNavigationScreen.SEARCH,"서울역",true);assertEquals(0,client.requests)
            client.connected();assertTrue(nav.state.value.connected)
            nav.open(VehicleNavigationScreen.MAP,parked=false);assertEquals(0,client.requests)
            nav.open(VehicleNavigationScreen.SEARCH,"서울역",true);assertEquals(1,client.requests)
            assertTrue(nav.state.value.message.contains("요청"))
            client.error("UNAVAILABLE");assertTrue(nav.state.value.message.contains("실패"))
            nav.stop();assertTrue(client.released);client.connected();assertFalse(nav.state.value.connected)
        } finally {scope.cancel()}
    }
    @Test fun destinationRequiresARealCallbackAndClearsOnErrorStopAndLateGeneration() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val client=Client();val nav=PleosNavigation({client},scope,{1234})
            nav.start();client.connected()
            assertTrue(client.destinationRequests>0);assertNull(nav.state.value.remainingSeconds)
            client.destination(DestinationEstimate("검증 목적지",2700,42000))
            assertEquals(2700,nav.state.value.remainingSeconds);assertEquals(42000,nav.state.value.remainingMeters)
            assertEquals(1234L,nav.state.value.readAtMs)
            client.destination(DestinationEstimate("검증 목적지",-1,42000));assertNull(nav.state.value.readAtMs)
            client.destination(DestinationEstimate("검증 목적지",2700,42000))
            client.error("UNAVAILABLE");assertNull(nav.state.value.remainingSeconds)
            val oldCallback=client.destination
            nav.stop();nav.start();client.connected();oldCallback(DestinationEstimate("이전 경로",2700,42000))
            assertNull(nav.state.value.remainingSeconds)
            client.destination(DestinationEstimate("새 경로",1200,10000));client.destination(null)
            assertNull(nav.state.value.remainingSeconds)
            nav.stop()
        } finally {scope.cancel()}
    }
    @Test fun missingSdkDoesNotBecomeAConnectedService() {
        val nav=PleosNavigation(null);nav.start()
        assertFalse(nav.state.value.included);assertFalse(nav.state.value.connected)
    }
}
