package io.cabinos.one.platform

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PleosNavigationTest {
    private class Client: PleosNavigationClient {
        lateinit var connected: () -> Unit
        lateinit var error: (String) -> Unit
        var requests=0
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
    @Test fun missingSdkDoesNotBecomeAConnectedService() {
        val nav=PleosNavigation(null);nav.start()
        assertFalse(nav.state.value.included);assertFalse(nav.state.value.connected)
    }
}
