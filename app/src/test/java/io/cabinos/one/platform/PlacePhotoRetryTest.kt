package io.cabinos.one.platform

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacePhotoRetryTest {
    @Test fun temporaryFailureRetriesAfterNegativeCacheExpires()=runTest {
        var calls=0
        val states=mutableListOf<Boolean>()
        val result=async {retryPlacePhoto(load={if(++calls==1)null else "photo"},loading={states+=it})}
        runCurrent();assertEquals(1,calls)
        advanceTimeBy(60_000);runCurrent();assertEquals(1,calls)
        advanceTimeBy(1_000);runCurrent()
        assertEquals("photo",result.await());assertEquals(2,calls)
        assertEquals(listOf(true,false,true,false),states)
    }
    @Test fun permanentFailureStopsAfterThreeAttempts()=runTest {
        var calls=0
        val result=async {retryPlacePhoto<String>(load={calls++;throw java.io.IOException()},loading={})}
        advanceUntilIdle()
        assertNull(result.await());assertEquals(3,calls)
    }
    @Test fun leavingScreenCancelsDelayedRetry()=runTest {
        var calls=0
        val job=launch {retryPlacePhoto<String>(load={calls++;null},loading={})}
        runCurrent();job.cancelAndJoin();advanceUntilIdle()
        assertEquals(1,calls)
    }
    @Test fun retryStateIsPublishedDuringDelayAndClearedBeforeLoading()=runTest {
        val waits=mutableListOf<Long>();var calls=0
        val result=async {retryPlacePhoto(load={if(++calls==1)null else "photo"},loading={},waiting={waits+=it})}
        runCurrent();assertEquals(61000L,waits.last())
        advanceUntilIdle()
        assertEquals("photo",result.await());assertEquals(0L,waits.last())
    }

}
