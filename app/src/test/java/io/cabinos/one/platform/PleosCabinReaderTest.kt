package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PleosCabinReaderTest {
    private class Client: PleosCabinClient {
        var passenger:Boolean?=true
        var readiness:List<String>?=listOf("창문이 열려 있어요","실내 온도 29°C")
        override suspend fun readiness()=readiness
        var reads=0;var released=false;var stalled=false
        override fun initialize() {}
        override suspend fun passenger():Boolean? { reads++;if(stalled)delay(4000);return passenger }
        override fun release() { released=true }
    }
    @Test fun passengerReadIsOptionalAndClearedWhenBackgrounded()=runTest {
        val client=Client();val reader=PleosCabinReader({client},backgroundScope)
        reader.start();runCurrent();assertEquals(true,reader.state.value.passengerDetected)
        reader.stop();runCurrent();assertNull(reader.state.value.passengerDetected);assertTrue(client.released)
        val reads=client.reads;advanceTimeBy(10000);assertEquals(reads,client.reads)
    }
    @Test fun unsupportedOrTimedOutPassengerDoesNotInventOccupancy()=runTest {
        val client=Client().apply { passenger=null };val reader=PleosCabinReader({client},backgroundScope)
        reader.start();runCurrent();assertNull(reader.state.value.passengerDetected)
        client.passenger=true;advanceTimeBy(1000);runCurrent();assertEquals(true,reader.state.value.passengerDetected)
        client.stalled=true;advanceTimeBy(4000);runCurrent();assertNull(reader.state.value.passengerDetected)
        reader.stop()
    }
    @Test fun readinessClearsOnUnsupportedAndStopWithoutBlockingPassenger()=runTest {
        val client=Client();val reader=PleosCabinReader({client},backgroundScope)
        reader.start();runCurrent();assertEquals(2,reader.state.value.readiness?.size)
        client.readiness=null;advanceTimeBy(1000);runCurrent()
        assertNull(reader.state.value.readiness);assertEquals(true,reader.state.value.passengerDetected)
        reader.stop();assertNull(reader.state.value.readiness)
    }
}
