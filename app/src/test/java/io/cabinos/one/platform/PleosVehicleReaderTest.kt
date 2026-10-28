package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosVehicleReaderTest {
    private class FakeClient : PleosBrakeClient {
        var initialized=false
        var released=false
        var reads=0
        var brakeChecks=0
        var energyChecks=0
        var switchChecks=0
        var energyReads=0
        var switchReads=0
        var readFailure: Exception? = null
        var capability: Boolean? = true
        var result: Boolean? = false
        var failure: Exception? = null
        var pending: ((Boolean?) -> Unit)? = null
        var deferred=false
        var energy: Int? = null
        var energySupported=true
        var chargeSwitch: Boolean? = null
        var switchSupported=true
        override fun canReadChargeSwitch(onComplete:(Boolean)->Unit,onFailed:(Exception)->Unit) = run { switchChecks++;onComplete(switchSupported) }
        override fun readChargeSwitch(onComplete:(Boolean?)->Unit,onFailed:(Exception)->Unit) = run { switchReads++;readFailure?.let { onFailed(it);return };onComplete(chargeSwitch) }
        override fun canReadEnergy(onComplete:(Boolean)->Unit,onFailed:(Exception)->Unit) = run { energyChecks++;onComplete(energySupported) }
        override fun readEnergy(onComplete:(Int?)->Unit,onFailed:(Exception)->Unit) = run { energyReads++;readFailure?.let { onFailed(it);return };onComplete(energy) }
        override fun initialize(){initialized=true}
        override fun canRead(onComplete:(Boolean)->Unit,onFailed:(Exception)->Unit) {
            brakeChecks++
            failure?.let {onFailed(it);return}
            capability?.let(onComplete)
        }
        override fun read(onComplete:(Boolean?)->Unit,onFailed:(Exception)->Unit) {
            reads++;readFailure?.let { onFailed(it);return };if(deferred)pending=onComplete else onComplete(result)
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
    @Test fun chargeSwitchReadIsIndependentAndClearsOnStop() {
        for(value in listOf(true,false,null)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            try {
                val client=FakeClient().apply { energy=75;chargeSwitch=value;failure=SecurityException("brake denied") }
                val reader=PleosVehicleReader({client},scope,{1234})
                reader.start()
                assertEquals(75,reader.energy.value.percent)
                assertEquals(value,reader.energy.value.chargeSwitchOn)
                assertEquals(if(value==null)null else 1234L,reader.energy.value.chargeReadAtMs)
                reader.stop();assertNull(reader.energy.value.chargeSwitchOn)
                client.switchSupported=false;reader.start();assertNull(reader.energy.value.chargeSwitchOn);reader.stop()
            } finally {scope.cancel()}
        }
    }
    @Test fun buildWithoutOfficialSdkExplicitlyReportsAbsence() {
        val reader=PleosVehicleReader(null)
        reader.start();assertFalse(reader.state.value.included)
        assertTrue(reader.state.value.message.contains("미포함"));assertNull(reader.state.value.brakePressed)
        reader.stop()
    }
    @Test fun evBatteryIsIndependentOfBrakeAndRejectsInvalidPercent() {
        for(value in listOf(68,0,100,-1,101)) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            try {
                val client=FakeClient().apply { energy=value;failure=SecurityException("brake denied") }
                val reader=PleosVehicleReader({client},scope,{1234})
                reader.start()
                assertEquals(if(value in 0..100) value else null,reader.energy.value.percent)
                assertNull(reader.state.value.brakePressed)
                reader.stop();assertNull(reader.energy.value.percent)
            } finally {scope.cancel()}
        }
    }
    @Test fun tenSuccessfulReadsCheckCapabilitiesOncePerConnection() = runTest {
        val client=FakeClient().apply { energy=68;chargeSwitch=true }
        val reader=PleosVehicleReader({client},backgroundScope,{testScheduler.currentTime})
        reader.start();runCurrent();advanceTimeBy(9000);runCurrent()
        assertEquals(listOf(10,10,10),listOf(client.reads,client.energyReads,client.switchReads))
        assertEquals(listOf(1,1,1),listOf(client.brakeChecks,client.energyChecks,client.switchChecks))
        reader.stop();runCurrent();reader.start();runCurrent()
        assertEquals(listOf(2,2,2),listOf(client.brakeChecks,client.energyChecks,client.switchChecks));reader.stop()
    }
    @Test fun readFailureInvalidatesEachCapabilityAndClearsValues() = runTest {
        val client=FakeClient().apply { energy=68;chargeSwitch=true }
        val reader=PleosVehicleReader({client},backgroundScope)
        reader.start();runCurrent();client.readFailure=SecurityException("denied")
        advanceTimeBy(1000);runCurrent()
        assertNull(reader.state.value.brakePressed);assertNull(reader.energy.value.percent);assertNull(reader.energy.value.chargeSwitchOn)
        assertEquals(listOf(1,1,1),listOf(client.brakeChecks,client.energyChecks,client.switchChecks))
        client.readFailure=null;advanceTimeBy(1000);runCurrent()
        assertEquals(listOf(2,2,2),listOf(client.brakeChecks,client.energyChecks,client.switchChecks))
        assertEquals(68,reader.energy.value.percent);reader.stop()
    }
    @Test fun unsupportedCapabilityIsCachedWithoutPollingItAgain() = runTest {
        val client=FakeClient().apply { capability=false;switchSupported=false }
        val reader=PleosVehicleReader({client},backgroundScope)
        reader.start();runCurrent();advanceTimeBy(9000);runCurrent()
        assertEquals(1,client.brakeChecks);assertEquals(1,client.switchChecks)
        assertEquals(0,client.reads);assertEquals(0,client.switchReads);reader.stop()
    }
    @Test fun timedOutReadRechecksCapabilityAndIgnoresLateReply() = runTest {
        val client=FakeClient().apply { energy=68;chargeSwitch=true }
        val reader=PleosVehicleReader({client},backgroundScope)
        reader.start();runCurrent();client.deferred=true
        advanceTimeBy(1000);runCurrent();val late=client.pending!!
        advanceTimeBy(3000);runCurrent()
        assertNull(reader.state.value.brakePressed);assertEquals(1,client.brakeChecks)
        late(true);assertNull(reader.state.value.brakePressed)
        client.deferred=false;advanceTimeBy(1000);runCurrent()
        assertEquals(2,client.brakeChecks);assertEquals(false,reader.state.value.brakePressed);reader.stop()
    }
    @Test fun unreadableCapabilitiesRetryAfterThirtySecondsAndRecover() = runTest {
        val client=FakeClient().apply { capability=false;energySupported=false;switchSupported=false;energy=68;chargeSwitch=true }
        val reader=PleosVehicleReader({client},backgroundScope,{testScheduler.currentTime})
        reader.start();runCurrent();advanceTimeBy(29000);runCurrent()
        assertEquals(listOf(1,1,1),listOf(client.brakeChecks,client.energyChecks,client.switchChecks))
        assertEquals(listOf(0,0,0),listOf(client.reads,client.energyReads,client.switchReads))
        client.capability=true;client.energySupported=true;client.switchSupported=true
        advanceTimeBy(1000);runCurrent()
        assertEquals(listOf(2,2,2),listOf(client.brakeChecks,client.energyChecks,client.switchChecks))
        assertEquals(68,reader.energy.value.percent);assertEquals(true,reader.energy.value.chargeSwitchOn)
        advanceTimeBy(60000);runCurrent()
        assertEquals(listOf(2,2,2),listOf(client.brakeChecks,client.energyChecks,client.switchChecks));reader.stop()
    }
    @Test fun stillUnreadableCapabilitiesRetryOnlyEveryThirtySeconds() = runTest {
        val client=FakeClient().apply { capability=false;energySupported=false;switchSupported=false }
        val reader=PleosVehicleReader({client},backgroundScope,{testScheduler.currentTime})
        reader.start();runCurrent();advanceTimeBy(60000);runCurrent()
        assertEquals(listOf(3,3,3),listOf(client.brakeChecks,client.energyChecks,client.switchChecks))
        assertNull(reader.energy.value.percent);assertNull(reader.state.value.brakePressed);reader.stop()
    }
}
