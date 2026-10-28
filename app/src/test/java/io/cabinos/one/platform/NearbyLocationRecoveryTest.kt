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
    @Test fun disablingUnusedNetworkProviderStillWaitsForGps()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        try {
            shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER,false)
            shadowOf(Looper.getMainLooper()).idle();testScheduler.runCurrent()
            assertFalse(task.isCompleted)
            shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first().onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {
                latitude=37.5;longitude=127.0;accuracy=5f;time=System.currentTimeMillis()
            })
            assertNotNull(task.await())
        } finally {task.cancelAndJoin()}
    }
    @Test fun legacyProviderCallbacksKeepWaitingForActualLocation()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        try {
            val listener=shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            listener.onProviderEnabled(LocationManager.GPS_PROVIDER)
            listener.onStatusChanged(LocationManager.GPS_PROVIDER,android.location.LocationProvider.AVAILABLE,null)
            assertFalse(task.isCompleted)
        } finally {task.cancelAndJoin()}
    }
    @Test fun preciseRequestStopsAfterPermissionDowngrade()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        val listener=shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        listener.onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {
            latitude=37.5;longitude=127.0;accuracy=5f;time=System.currentTimeMillis()
        })
        assertNull(task.await())
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
    }
    @Test fun queuedPreciseLocationIsRejectedAfterPermissionRevocation()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        val listener=shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        listener.onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {
            latitude=37.5;longitude=127.0;accuracy=5f;time=System.currentTimeMillis()
        })
        assertNull(task.await())
        assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
    }
    @Test fun disablingEveryProviderEndsNearbyWaitImmediately()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        val shadow=shadowOf(manager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER,false)
        val task=async(start=CoroutineStart.UNDISPATCHED) {NearbyPlaces(context).locate()}
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER,false)
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.runCurrent()
        try {
            assertTrue("위치 서비스가 꺼지면 25초 전체를 기다리지 않아야 합니다",task.isCompleted)
            assertNull(task.await())
            assertTrue(shadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
        } finally {task.cancelAndJoin()}
    }

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
