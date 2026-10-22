package io.cabinos.one.platform

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class LiveNavigationTest {
    @Test fun androidFrameworkGpsDrivesNavigationAndEndingWhileMovingKeepsSafetyLock() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java);shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        var now=System.currentTimeMillis();val engine=UnifiedVehicleContextEngine({now})
        fun park(){engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))}
        park();now+=1600;park();assertTrue(engine.state.value.parkedAllowed)
        val points=listOf(NavPoint(48.85,2.35),NavPoint(48.86,2.35),NavPoint(48.87,2.36))
        val end=NavPlace("Paris",NavPoint(48.87,2.363))
        val service=object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=NavigationGeometry.route(end,points,3000.0,600.0,listOf(NavManeuver(points[0],"depart"),NavManeuver(points[1],"turn","left"),NavManeuver(points[2],"arrive")))}
        val speech=AndroidSpeechProvider(context);val nav=LiveNavigation(context,engine,speech,service)
        fun gps(p:NavPoint,speed:Float){shadowOf(manager).simulateLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {latitude=p.latitude;longitude=p.longitude;accuracy=5f;this.speed=speed;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()});shadowOf(Looper.getMainLooper()).idle()}
        try {
            nav.locate();gps(points.first(),0f);nav.plan(end);shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(nav.state.value.guidance.route);nav.start();assertFalse(nav.state.value.guidance.active);nav.start(true);assertTrue(nav.state.value.guidance.active);assertFalse(engine.state.value.parkedAllowed)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2));gps(NavPoint(48.851,2.35),10f)
            assertTrue(nav.state.value.guidance.remainingMeters<3000);nav.stop();assertFalse(nav.state.value.guidance.active);assertFalse(engine.state.value.parkedAllowed)
            nav.setForeground(false);assertNull(nav.state.value.guidance.route)
        }finally{nav.close();speech.close()}
    }
}
