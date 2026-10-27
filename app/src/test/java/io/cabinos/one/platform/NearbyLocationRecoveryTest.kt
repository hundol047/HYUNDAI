package io.cabinos.one.platform

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NearbyLocationRecoveryTest {
    @Test fun inaccurateFirstFixDoesNotConsumeTheOnlyLocationRequest()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        try {
            fun emit(accuracyMeters:Float) {
                shadow.simulateLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                    latitude=37.5;longitude=127.0;accuracy=accuracyMeters;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
                })
                shadowOf(Looper.getMainLooper()).idle()
            }
            emit(500f)
            assertFalse(task.isCompleted)
            assertTrue("부정확한 첫 위치 뒤에도 갱신을 기다려야 합니다",shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isNotEmpty())
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            emit(5f)
            val result=task.await()
            assertNotNull(result)
            assertEquals(37.5,result!!.latitude,0.000001)
            assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
        } finally {task.cancelAndJoin()}
    }
    @Test fun locationTimeoutRemovesSubscriptionsWithoutRelaxingAccuracy()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        testScheduler.advanceTimeBy(25_001)
        assertNull(task.await())
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
    }
    @Test fun cancellingNearbyLocationRemovesItsSubscriptions()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isNotEmpty())
        task.cancelAndJoin()
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
    }
}
