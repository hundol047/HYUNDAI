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
            f.advance(20);f.gps();assertEquals("검사용 경로 오류",f.nav.state.value.notice);assertTrue(f.nav.state.value.gpsNotice.contains("GPS 확인"))
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
            f.advance(20);f.gps();f.park();assertFalse(f.engine.state.value.parkedAllowed)
            repeat(5){f.advance(1000);f.gps();f.park()}
            f.now+=1600;f.park();assertTrue(f.engine.state.value.parkedAllowed)
        }
    }
    @Test fun stationaryFrameworkUpdatesKeepGpsFreshAfterTwelveSeconds() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate()
            repeat(13){
                if(it>0)f.advance(1000)
                shadowOf(f.manager).simulateLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {latitude=origin.latitude;longitude=origin.longitude;accuracy=5f;speed=0f;time=f.now;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()})
                f.idle();f.park();f.nav.tick()
                assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
                assertEquals(0L,f.nav.state.value.gpsAgeSeconds)
            }
            f.nav.plan(destination);f.idle();assertTrue(f.nav.state.value.originReady)
        }
    }
    @Test fun reconnectRegistersANewListenerAndIgnoresRetiredCallbacks() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps()
            val old=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            f.nav.locate();assertEquals(GpsConnection.WAITING,f.nav.state.value.gpsConnection)
            val listeners=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER)
            assertEquals(1,listeners.size);assertNotSame(old,listeners.first())
            f.advance(20);old.onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {latitude=0.0;longitude=0.0;accuracy=5f;time=f.now})
            assertEquals(GpsConnection.WAITING,f.nav.state.value.gpsConnection)
            f.gps();assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
        }
    }
    @Test fun noFirstFixTimesOutAndCanBeReconnected() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();repeat(16){f.advance(1000);f.park();f.nav.tick()}
            assertEquals(GpsConnection.STALE,f.nav.state.value.gpsConnection);assertFalse(f.nav.state.value.originReady)
            f.nav.locate();assertEquals(GpsConnection.WAITING,f.nav.state.value.gpsConnection)
            f.gps();assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
        }
    }
    @Test fun permissionRevocationClearsFixAndGrantingItAllowsANewSubscription() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps()
            shadowOf(f.context as Application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            f.nav.tick();assertEquals(GpsConnection.DENIED,f.nav.state.value.gpsConnection)
            assertNull(f.nav.state.value.gpsAccuracy);assertTrue(shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
            f.nav.locate();assertEquals(GpsConnection.DENIED,f.nav.state.value.gpsConnection)
            shadowOf(f.context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            f.nav.locate();f.advance(20);f.gps();assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
        }
    }
    @Test fun disabledProviderCanBeEnabledAndResubscribed() {
        Fixture(NavigationService()).use {f->
            shadowOf(f.manager).setProviderEnabled(LocationManager.GPS_PROVIDER,false)
            shadowOf(f.manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER,false)
            f.nav.locate();assertEquals(GpsConnection.DISABLED,f.nav.state.value.gpsConnection)
            shadowOf(f.manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
            f.nav.locate();f.gps();assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
        }
    }
    @Test fun settingsIntentsAreRealAndBlockedAfterMovement() {
        Fixture(NavigationService()).use {f->
            val app=shadowOf(f.context as Application)
            f.nav.openLocationSettings();assertEquals(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS,app.nextStartedActivity.action)
            f.nav.openLocationSettings(true);val intent=app.nextStartedActivity
            assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,intent.action)
            assertEquals("package:${f.context.packageName}",intent.data.toString())
            f.nav.locate();f.gps(speed=10f);f.nav.openLocationSettings();assertNull(app.nextStartedActivity)
        }
    }
    @Test fun backgroundTimeCannotCountAsStationaryEvidence() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps(speed=10f)
            repeat(3){f.advance(1000);f.gps()}
            f.nav.setForeground(false);f.advance(6000);f.nav.setForeground(true);f.gps();f.park()
            assertFalse(f.engine.state.value.parkedAllowed)
            repeat(5){f.advance(1000);f.gps();f.park()}
            f.now+=1600;f.park();assertTrue(f.engine.state.value.parkedAllowed)
        }
    }
    @Test fun staleGpsIsReportedAndFreshFixRecoversWithoutAFakePosition() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle()
            f.advance(10_001);f.nav.tick();assertEquals(GpsConnection.STALE,f.nav.state.value.gpsConnection);assertFalse(f.nav.state.value.originReady)
            f.gps();assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection);assertTrue(f.nav.state.value.originReady)
        }
    }

    @Test fun recoveredGpsRepeatsCurrentGuidanceThroughTheAndroidTtsApi() {
        org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.KOREAN)
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            val tts=shadowOf(org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle();f.nav.start()
            assertTrue(f.nav.state.value.guidance.tracking);val count=tts.spokenTextList.size;assertTrue(count>0)
            val listener=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            listener.onProviderDisabled(LocationManager.GPS_PROVIDER);assertFalse(f.nav.state.value.guidance.tracking)
            f.advance(1000);f.gps();assertTrue(f.nav.state.value.guidance.tracking)
            assertTrue(tts.spokenTextList.size>count)
        }
    }

    @Test fun coarseNetworkFixDoesNotInterruptFreshGpsButPoorGpsStillStopsReadiness() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle();assertTrue(f.nav.state.value.originReady)
            val listener=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            f.advance(1000);listener.onLocationChanged(Location(LocationManager.NETWORK_PROVIDER).apply {latitude=origin.latitude;longitude=origin.longitude;accuracy=200f;time=f.now})
            assertTrue(f.nav.state.value.originReady);assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
            f.advance(1000);f.gps(accuracy=200f);assertFalse(f.nav.state.value.originReady);assertEquals(GpsConnection.POOR,f.nav.state.value.gpsConnection)
        }
    }

    @Test fun staleInactiveGpsDoesNotCancelAnAssistantUtteranceEveryTick() {
        org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.KOREAN)
        Fixture(NavigationService()).use {f->
            val tts=shadowOf(org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
            f.nav.locate();repeat(16){f.advance(1000);f.park();f.nav.tick()}
            assertEquals(GpsConnection.STALE,f.nav.state.value.gpsConnection)
            f.speech.speak("검사용 어시스턴트 응답");assertFalse(tts.isStopped)
            repeat(3){f.advance(1000);f.park();f.nav.tick()}
            assertFalse(tts.isStopped);assertEquals("검사용 어시스턴트 응답",tts.lastSpokenText)
        }
    }

}
