package io.cabinos.one.platform

import io.cabinos.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class RequestTimingTest {
    @Test fun clockCorrectionDoesNotStrandNextPlaceSearch()=runTest {
        var now=3_600_000L
        var calls=0
        val service=object:NavigationService({now}) {
            override suspend fun get(url:String,accessToken:String):String {calls++;return "[]"}
        }
        service.search("first")
        now-=3_600_000L
        val next=async {service.search("second")}
        runCurrent();advanceTimeBy(1501);runCurrent()
        try {assertEquals("Time correction must not delay a search for an hour",2,calls)}
        finally {next.cancelAndJoin()}
    }
    @Test fun immediateSearchesStillRespectProviderInterval()=runTest {
        var calls=0
        val service=object:NavigationService({3_600_000L+testScheduler.currentTime}) {
            override suspend fun get(url:String,accessToken:String):String {calls++;return "[]"}
        }
        service.search("first")
        val next=async {service.search("second")}
        runCurrent();advanceTimeBy(1499);runCurrent();assertEquals(1,calls)
        advanceTimeBy(2);runCurrent();assertEquals(2,calls);next.await()
    }
}
