package io.cabinos.one.platform

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class LocationLifecycleTest {
    @Test fun legacyProviderCallbacksDoNotCrashOrInventAvailability() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val provider=AndroidLocationProvider(context)
        try {
            provider.start()
            val listener=shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            listener.onProviderEnabled(LocationManager.GPS_PROVIDER)
            listener.onStatusChanged(LocationManager.GPS_PROVIDER,android.location.LocationProvider.AVAILABLE,null)
            assertFalse(provider.available.value);assertNull(provider.lastLocation)
        } finally {provider.stop()}
    }
    @Test fun stoppedAndPreviousSubscriptionsCannotRestoreLocationAvailability() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val provider=AndroidLocationProvider(context)
        fun fix()=Location(LocationManager.GPS_PROVIDER).apply {latitude=37.5;longitude=127.0;time=System.currentTimeMillis()}
        provider.start()
        val old=shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
        old.onLocationChanged(fix());assertTrue(provider.available.value)
        provider.stop();old.onLocationChanged(fix())
        assertFalse(provider.available.value);assertNull(provider.lastLocation)
        provider.start();old.onLocationChanged(fix())
        assertFalse(provider.available.value);assertNull(provider.lastLocation)
        val current=shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
        current.onLocationChanged(fix());assertTrue(provider.available.value)
        provider.stop()
    }
    @Test fun revokedPermissionRejectsQueuedLocationAndClearsPreviousFix() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        val provider=AndroidLocationProvider(context)
        provider.start()
        val listener=shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
        val fix=Location(LocationManager.GPS_PROVIDER).apply {latitude=37.5;longitude=127.0;time=System.currentTimeMillis()}
        listener.onLocationChanged(fix);assertTrue(provider.available.value)
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        listener.onLocationChanged(fix)
        assertFalse(provider.available.value);assertNull(provider.lastLocation)
        provider.stop()
    }
}
