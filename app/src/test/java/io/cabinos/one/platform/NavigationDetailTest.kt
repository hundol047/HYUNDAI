package io.cabinos.one.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NavigationDetailTest {
    private val origin=NavPoint(48.85,2.35)
    private val destination=NavPlace("검사용 가게",NavPoint(48.86,2.35))
    private fun route()=NavigationGeometry.route(destination,listOf(origin,destination.point),1200.0,180.0,listOf(NavManeuver(origin,"depart"),NavManeuver(destination.point,"arrive")))
    private inner class Fixture(service:NavigationService):AutoCloseable {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=context.getSystemService(LocationManager::class.java)
        var now=System.currentTimeMillis()
        val engine=UnifiedVehicleContextEngine({now})
        val speech=AndroidSpeechProvider(context)
        val nav=LiveNavigation(context,engine,speech,service,{now})
        init {
            shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
            park();now+=1600;park();assertTrue(engine.state.value.parkedAllowed)
        }
        fun park(){engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))}
        fun advance(ms:Long){ShadowSystemClock.advanceBy(Duration.ofMillis(ms));now+=ms}
        fun gps(accuracy:Float=5f,speed:Float=0f,at:Long=now) {
            // Deliver directly through the framework-registered callbacks so
            // provider minTime/minDistance filtering cannot hide old/bad fixes.
            val callbacks=shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER)
            assertTrue(callbacks.isNotEmpty())
            val location=Location(LocationManager.GPS_PROVIDER).apply {
                latitude=origin.latitude;longitude=origin.longitude;this.accuracy=accuracy;this.speed=speed;time=at;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
            };callbacks.forEach {it.onLocationChanged(location)};idle()
        }
        fun idle(){shadowOf(Looper.getMainLooper()).idle()}
        override fun close(){nav.close();speech.close()}
    }
    @Test fun inaccurateAndOutOfOrderGpsCannotStartFromAnEarlierGoodFix() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle();assertTrue(f.nav.state.value.originReady)
            f.advance(20);val badAt=f.now;f.gps(accuracy=200f)
            assertFalse(f.nav.state.value.originReady);f.nav.start();assertFalse(f.nav.state.value.guidance.active)
            f.gps(at=badAt-1);assertFalse(f.nav.state.value.originReady)
            f.advance(20);f.gps();assertTrue(f.nav.state.value.originReady)
        }
    }
    @Test fun freshGpsDoesNotEraseRouteErrors() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace):DrivingRoute=error("검사용 경로 오류")}).use {f->
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle();assertEquals("검사용 경로 오류",f.nav.state.value.notice)
            f.advance(20);f.gps();assertEquals("검사용 경로 오류",f.nav.state.value.notice);assertTrue(f.nav.state.value.gpsNotice.contains("GPS 위치 확인"))
        }
    }
    @Test fun cancelledSearchCannotPublishLateResultsOverANewSelection() {
        val pending=CompletableDeferred<PlaceSearchPage>()
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>)=withContext(NonCancellable){pending.await()}}).use {f->
            f.nav.search("늦은 검색");f.idle();assertTrue(f.nav.state.value.busy)
            f.nav.cancelPending();assertFalse(f.nav.state.value.busy)
            f.nav.plan(destination);assertEquals(destination,f.nav.state.value.selectedPlace)
            pending.complete(PlaceSearchPage(listOf(NavPlace("이전 결과",origin)),emptyList(),false));f.idle()
            assertTrue(f.nav.state.value.places.isEmpty());assertEquals(destination,f.nav.state.value.selectedPlace);assertNull(f.nav.state.value.guidance.route)
        }
    }
    @Test fun backgroundCallbacksAreIgnoredAndForegroundGetsANewSubscription() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle()
            val retired=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            f.nav.setForeground(false);assertFalse(f.nav.state.value.originReady)
            f.advance(20);retired.onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {latitude=0.0;longitude=0.0;accuracy=5f;time=f.now})
            assertFalse(f.nav.state.value.originReady)
            f.nav.setForeground(true);f.advance(20);f.gps();assertTrue(f.nav.state.value.originReady)
        }
    }
    @Test fun duplicateStationaryGpsCannotReleaseMotionSafety() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps(speed=10f);assertFalse(f.engine.state.value.parkedAllowed)
            f.advance(20);val stationaryAt=f.now;f.gps()
            f.advance(6000);f.gps(at=stationaryAt);f.park();assertFalse(f.engine.state.value.parkedAllowed)
            f.advance(20);f.gps();f.park();f.now+=1600;f.park();assertTrue(f.engine.state.value.parkedAllowed)
        }
    }
}
