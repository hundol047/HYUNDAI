package io.cabinos.one.platform

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PleosVehicleReaderTest {
    private class FakeClient : PleosBrakeClient {
        var initialized=false
        var released=false
        var reads=0
        var capability: Boolean? = true
        var result: Boolean? = false
        var failure: Exception? = null
        var pending: ((Boolean?) -> Unit)? = null
        var deferred=false
        override fun initialize(){initialized=true}
        override fun canRead(onComplete:(Boolean)->Unit,onFailed:(Exception)->Unit) {
            failure?.let {onFailed(it);return}
            capability?.let(onComplete)
        }
        override fun read(onComplete:(Boolean?)->Unit,onFailed:(Exception)->Unit) {
            reads++;if(deferred)pending=onComplete else onComplete(result)
        }
        override fun release(){released=true}
    }
    @Test fun initializationAloneIsNeverReportedAsReadSuccess() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val client=FakeClient().apply{capability=null}
            val reader=PleosVehicleReader({client},scope,{1234})
            reader.start();assertTrue(client.initialized);assertNull(reader.state.value.successfulReadAtMs)
            assertNull(reader.state.value.brakePressed);reader.stop();assertTrue(client.released)
        } finally {scope.cancel()}
    }
    @Test fun onlyACompletedSupportedReadCarriesARealValueAndTimestamp() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val client=FakeClient().apply{result=true}
            val reader=PleosVehicleReader({client},scope,{1234})
            reader.start();assertEquals(true,reader.state.value.brakePressed)
            assertEquals(1234L,reader.state.value.successfulReadAtMs)
            assertEquals(1,client.reads);reader.stop();assertNull(reader.state.value.brakePressed)
        } finally {scope.cancel()}
    }
    @Test fun unsupportedOrFailedCapabilitiesDoNotReadOrInventReleasedBrake() {
        for(failed in listOf(false,true)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            try {
                val client=FakeClient().apply{capability=false;if(failed)failure=SecurityException("denied")}
                val reader=PleosVehicleReader({client},scope)
                reader.start();assertEquals(0,client.reads);assertNull(reader.state.value.brakePressed)
                assertNull(reader.state.value.successfulReadAtMs);reader.stop()
            } finally {scope.cancel()}
        }
    }
    @Test fun nullValueAndLateResponseAfterStopNeverClaimConnection() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val client=FakeClient().apply{result=null}
            val reader=PleosVehicleReader({client},scope)
            reader.start();assertNull(reader.state.value.successfulReadAtMs);reader.stop()
            client.deferred=true;reader.start();reader.stop()
            client.pending!!.invoke(true);client.pending!!.invoke(false)
            assertNull(reader.state.value.brakePressed);assertTrue(client.released)
        } finally {scope.cancel()}
    }
    @Test fun buildWithoutOfficialSdkExplicitlyReportsAbsence() {
        val reader=PleosVehicleReader(null)
        reader.start();assertFalse(reader.state.value.included)
        assertTrue(reader.state.value.message.contains("미포함"));assertNull(reader.state.value.brakePressed)
        reader.stop()
    }
}
