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
    @Test fun automaticGuidanceDoesNotStartADestinationUtteranceThatItImmediatelyInterrupts() {
        org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.KOREAN)
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            val tts=shadowOf(org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
            f.nav.locate();f.gps();f.nav.navigateTo(destination);f.idle()
            assertTrue(f.nav.state.value.guidance.active)
            assertFalse("목적지 읽기를 시작하자마자 첫 안내로 끊으면 안 됩니다",tts.spokenTextList.contains(destination.name))
            assertTrue(tts.spokenTextList.isNotEmpty())
        }
    }

    @Test @Config(sdk=[35]) fun fusedProviderCanSupplyFreshPreciseOriginOnModernPhones() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            shadowOf(f.manager).setProviderEnabled(LocationManager.GPS_PROVIDER,false)
            shadowOf(f.manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER,false)
            shadowOf(f.manager).setProviderEnabled("fused",true)
            shadowOf(f.manager).setLastKnownLocation("fused",Location("fused").apply {
                latitude=origin.latitude;longitude=origin.longitude;accuracy=12f;time=f.now;speed=0f
            })
            f.nav.navigateTo(destination);f.idle()
            assertTrue(f.nav.state.value.guidance.active)
            assertTrue(f.nav.state.value.gpsNotice.contains("기기 위치"))
        }
    }

    @Test fun retryingSelectedDestinationRestartsStalledGpsSubscription() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate()
            val old=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            repeat(16){f.advance(1000);f.park();f.nav.tick()}
            assertTrue(f.engine.state.value.parkedAllowed)
            assertEquals(GpsConnection.STALE,f.nav.state.value.gpsConnection)
            f.nav.navigateTo(destination)
            val current=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            assertNotSame(old,current)
            f.gps();assertTrue(f.nav.state.value.guidance.active)
        }
    }
    @Test fun freshFrameworkCachedFixStartsRouteWithoutWaitingForMovement() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            shadowOf(f.manager).setLastKnownLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                latitude=origin.latitude;longitude=origin.longitude;accuracy=12f;time=f.now;speed=0f
            })
            f.nav.navigateTo(destination);f.idle()
            assertTrue(f.nav.state.value.guidance.active)
            assertEquals(f.now,f.nav.state.value.mapFix?.timestampMs)
        }
    }

    @Test fun spokenDestinationUsesCurrentGpsAndResultNumberStartsGuidance() {
        var searched="";var originUsed:NavPoint?=null
        Fixture(object:NavigationService(){
            override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {
                searched=query;originUsed=origin;return PlaceSearchPage(listOf(destination),listOf("1"),false)
            }
            override suspend fun route(origin:NavPoint,destination:NavPlace)=route()
        }).use {f->
            f.nav.locate();f.gps();f.nav.handleVoice("판교역까지 데려다줘");f.idle()
            assertEquals("판교역",searched);assertEquals(origin,originUsed)
            assertEquals("판교역",f.nav.state.value.searchQuery)
            assertFalse(f.nav.state.value.guidance.active)
            f.nav.handleVoice("1번");f.idle()
            assertTrue(f.nav.state.value.guidance.active);assertFalse(f.engine.state.value.parkedAllowed)
        }
    }
    @Test fun cancelledOneTapDestinationCannotStartWhenGpsArrivesLater() {
        var calls=0
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace):DrivingRoute{calls++;return route()}}).use {f->
            f.nav.navigateTo(destination);f.nav.cancelPending();f.gps()
            assertEquals(0,calls);assertFalse(f.nav.state.value.guidance.active)
        }
    }

    @Test fun regionalCandidatesRequireExplicitActionAndNeverAutomaticallyStartGuidance() {
        var calls=0
        val service=object:NavigationService(){override suspend fun regionalSearch(query:String,origin:NavPoint?):RegionalSearchResult {
            calls++;return RegionalSearchResult(listOf(destination),"Paris","Cafe")
        }}
        Fixture(service).use {f->
            assertEquals(0,calls);f.nav.searchRegional("Cafe Paris");f.idle()
            assertEquals(1,calls);assertEquals(destination,f.nav.state.value.places.single())
            assertTrue(f.nav.state.value.notice.contains("주변 후보"));assertFalse(f.nav.state.value.hasMore)
            assertNull(f.nav.state.value.guidance.route);assertNull(f.nav.state.value.selectedPlace)
            f.engine.acceptVehicle(VehicleSample(speedKph=35.0,gear=Gear.DRIVE,timestampMs=f.now));f.idle()
            f.nav.searchRegional("Cafe Paris");f.idle();assertEquals(1,calls)
        }
    }
    @Test fun cancellingRegionalRecoveryRejectsLateResultsFromTheOldRequest() {
        val pending=CompletableDeferred<RegionalSearchResult>()
        val service=object:NavigationService(){
            override suspend fun regionalSearch(query:String,origin:NavPoint?)=withContext(NonCancellable){pending.await()}
            override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>)=PlaceSearchPage(listOf(destination.copy(name="새 가게")),emptyList(),false)
        }
        Fixture(service).use {f->
            f.nav.searchRegional("Cafe Paris");f.idle();assertTrue(f.nav.state.value.busy)
            f.nav.cancelPending();f.nav.search("새 가게");f.idle()
            pending.complete(RegionalSearchResult(listOf(destination),"Paris","Cafe"));f.idle()
            assertEquals("새 가게",f.nav.state.value.places.single().name)
        }
    }
    @Test fun regionParserUsesRegisteredAliasesAndRejectsShopsOrInvalidCoordinates() {
        val raw="""[
          {"place_id":1,"name":"토론토","namedetails":{"name:en":"Toronto"},"lat":"43.65","lon":"-79.38","type":"administrative"},
          {"place_id":2,"name":"Toronto","lat":"43.65","lon":"-79.38","type":"cafe"},
          {"place_id":3,"name":"Toronto","lat":"NaN","lon":"-79.38","type":"city"}
        ]"""
        assertEquals(listOf("1"),NavigationService.parseRegions(raw,"Toronto").map {it.id})
    }
    @Test fun emptyBusinessSearchRetriesSpacingOnceAndMoreUsesTheResolvedQuery() {
        val calls=mutableListOf<Pair<String,List<String>>>()
        val service=object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {
            calls+=query to excluded
            if(query=="스타벅스 원주무실")return PlaceSearchPage(emptyList(),emptyList(),false)
            val places=(excluded.size until excluded.size+6).map {NavPlace("검사용 지점 $it",destination.point,id="$it",address="검사용 주소")}
            return PlaceSearch.page(places,origin,nearby,excluded)
        }}
        Fixture(service).use {f->
            f.nav.search("스타벅스 원주무실");f.idle()
            assertEquals(listOf("스타벅스 원주무실","스타벅스 원주 무실"),calls.map {it.first})
            assertEquals(6,f.nav.state.value.places.size);assertTrue(f.nav.state.value.notice.contains("띄어쓰기"));assertTrue(f.nav.state.value.hasMore)
            f.nav.search("스타벅스 원주무실",more=true);f.idle()
            assertEquals("스타벅스 원주 무실",calls.last().first);assertEquals(6,calls.last().second.size);assertEquals(12,f.nav.state.value.places.size)
        }
    }
    @Test fun cancellingSpacingRetryPreventsLatePlacesReplacingANewSearch() {
        val pending=CompletableDeferred<PlaceSearchPage>()
        val service=object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage = when(query){
            "스타벅스 원주무실"->PlaceSearchPage(emptyList(),emptyList(),false)
            "스타벅스 원주 무실"->withContext(NonCancellable){pending.await()}
            else->PlaceSearchPage(listOf(destination.copy(name="새 검색 결과",id="new")),listOf("new"),false)
        }}
        Fixture(service).use {f->
            f.nav.search("스타벅스 원주무실");f.idle();assertTrue(f.nav.state.value.busy)
            f.nav.cancelPending();f.nav.search("새 가게");f.idle()
            pending.complete(PlaceSearchPage(listOf(destination.copy(name="이전 결과",id="old")),listOf("old"),false));f.idle()
            assertEquals("새 검색 결과",f.nav.state.value.places.single().name)
        }
    }
    @Test fun changingPlanningContextCancelsDestinationWaitingForGps() {
        for(action in listOf("regional","servers","language","background")) {
            var routes=0
            Fixture(object:NavigationService(){
                override suspend fun route(origin:NavPoint,destination:NavPlace):DrivingRoute {routes++;return route()}
                override suspend fun regionalSearch(query:String,origin:NavPoint?)=RegionalSearchResult(listOf(destination),"Paris","Cafe")
            }).use {f->
                f.nav.navigateTo(destination)
                when(action) {
                    "regional"->f.nav.searchRegional("Cafe Paris")
                    "servers"->f.nav.configure("https://nominatim.openstreetmap.org","https://router.project-osrm.org")
                    "language"->f.nav.setLanguage("fr-FR")
                    "background"->{f.nav.setForeground(false);f.nav.setForeground(true)}
                }
                f.idle();f.gps()
                assertEquals("$action must cancel the old automatic destination",0,routes)
                assertFalse(f.nav.state.value.guidance.active)
            }
        }
    }
    @Test fun reconnectCanReuseTheSameStillFreshFrameworkFix() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps()
            shadowOf(f.manager).setLastKnownLocation(LocationManager.GPS_PROVIDER,Location(LocationManager.GPS_PROVIDER).apply {
                latitude=origin.latitude;longitude=origin.longitude;accuracy=5f;time=f.now;speed=0f
            })
            f.nav.locate();f.idle()
            assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
            assertEquals(f.now,f.nav.state.value.mapFix?.timestampMs)
        }
    }
    @Test fun offlineCoordinatesAreLocalAndUpdateTheVisibleQuery() {
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage=error("no network")}).use {f->
            f.engine.setConnectivity(network=false)
            f.nav.search("48.85,2.35")
            assertEquals(origin,f.nav.state.value.places.singleOrNull()?.point)
            assertEquals("48.85,2.35",f.nav.state.value.searchQuery)
            assertFalse(f.nav.state.value.busy)
        }
    }
    @Test fun blankAndOfflineTextSearchExplainWhyWithoutErasingResults() {
        var calls=0
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {
            calls++;return PlaceSearchPage(listOf(destination),emptyList(),false)
        }}).use {f->
            f.nav.search("Cafe");f.idle()
            f.nav.search("   ");f.idle()
            assertEquals(1,calls);assertEquals(listOf(destination),f.nav.state.value.places)
            assertTrue(f.nav.state.value.notice.contains("검색어"))
            f.engine.setConnectivity(network=false);f.nav.search("Other cafe")
            assertEquals(1,calls);assertEquals(listOf(destination),f.nav.state.value.places)
            assertTrue(f.nav.state.value.notice.contains("인터넷"))
        }
    }
    @Test fun cancellingGpsWaitReplacesThePendingInstruction() {
        Fixture(NavigationService()).use {f->
            f.nav.navigateTo(destination);f.nav.cancelPending()
            assertTrue(f.nav.state.value.notice,f.nav.state.value.notice.contains("취소"))
            f.gps();assertFalse(f.nav.state.value.guidance.active)
        }
    }

    @Test fun safetyLockCancelsGpsPendingDestinationEvenAfterParkingAgain() {
        var calls=0
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace):DrivingRoute {calls++;return route()}}).use {f->
            f.nav.navigateTo(destination);f.idle()
            f.engine.acceptVehicle(VehicleSample(speedKph=10.0,gear=Gear.DRIVE,timestampMs=f.now));f.idle()
            f.park();f.advance(1600);f.park();f.idle();f.gps()
            assertTrue(f.engine.state.value.parkedAllowed)
            assertEquals(0,calls);assertFalse(f.nav.state.value.guidance.active)
        }
    }
    @Test fun safetyLockRejectsLateRouteAfterParkingAgain() {
        val response=CompletableDeferred<DrivingRoute>()
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=withContext(NonCancellable){response.await()}}).use {f->
            f.nav.locate();f.gps();f.nav.navigateTo(destination);f.idle()
            assertTrue(f.nav.state.value.busy)
            f.engine.invalidateTelemetry();f.idle()
            f.park();f.advance(1600);f.park();f.idle()
            response.complete(route());f.idle()
            assertFalse(f.nav.state.value.guidance.active)
            assertNull(f.nav.state.value.guidance.route)
            assertFalse(f.nav.state.value.busy)
        }
    }
    @Test fun safetyLockRejectsLateSearchAfterParkingAgain() {
        val response=CompletableDeferred<PlaceSearchPage>()
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>)=withContext(NonCancellable){response.await()}}).use {f->
            f.nav.search("Cafe");f.idle()
            f.engine.invalidateTelemetry();f.idle()
            f.park();f.advance(1600);f.park();f.idle()
            response.complete(PlaceSearchPage(listOf(destination),emptyList(),false));f.idle()
            assertTrue(f.nav.state.value.places.isEmpty())
            assertFalse(f.nav.state.value.busy)
        }
    }

    @Test fun legacyProviderCallbacksDoNotCrashOrInventAFix() {
        Fixture(NavigationService()).use {f->
            f.nav.locate()
            val listener=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            listener.onProviderEnabled(LocationManager.GPS_PROVIDER)
            listener.onStatusChanged(LocationManager.GPS_PROVIDER,android.location.LocationProvider.AVAILABLE,null)
            assertNull(f.nav.state.value.mapFix)
            assertEquals(GpsConnection.WAITING,f.nav.state.value.gpsConnection)
        }
    }
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

    @Test fun ambiguousOpposingRoadsStopAndroidTtsAndPreciseCourseResumesIt() {
        val points=listOf(origin,NavPoint(48.852,2.35),NavPoint(48.852,2.3503),NavPoint(48.85,2.3503))
        val goal=NavPlace("GPS ambiguity fixture",points.last())
        val loop=NavigationGeometry.route(goal,points,478.0,90.0,listOf(NavManeuver(points[0],"depart"),NavManeuver(points[1],"turn","right"),NavManeuver(points[3],"arrive")))
        org.robolectric.shadows.ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.KOREAN)
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=loop}).use {f->
            val tts=shadowOf(org.robolectric.shadows.ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(android.speech.tts.TextToSpeech.SUCCESS)
            f.nav.locate();f.gps();f.nav.plan(goal);f.idle();f.nav.start()
            fun position(point:NavPoint,accuracy:Float,bearing:Float?) {
                val location=Location(LocationManager.GPS_PROVIDER).apply {
                    latitude=point.latitude;longitude=point.longitude;this.accuracy=accuracy;time=f.now
                    if(bearing!=null){this.bearing=bearing;speed=5f}
                }
                shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).forEach {it.onLocationChanged(location)};f.idle()
            }
            f.advance(30000);position(points[2],5f,180f)
            val count=tts.spokenTextList.size;assertTrue(count>0)
            f.advance(5000);position(NavPoint(48.8515,2.35015),25f,null)
            assertTrue(f.nav.state.value.guidance.directionUncertain);assertTrue(tts.isStopped)
            f.nav.handleVoice("안내 반복");assertEquals(count,tts.spokenTextList.size)
            f.advance(1000);position(NavPoint(48.8514,2.3503),5f,180f)
            assertFalse(f.nav.state.value.guidance.directionUncertain);assertTrue(tts.spokenTextList.size>count)
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

    private fun enableMicrophone(context:Context){
        shadowOf(context as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val info=android.content.pm.ResolveInfo().apply {serviceInfo=android.content.pm.ServiceInfo().apply {packageName="test.speech";name="RecognitionService"}}
        shadowOf(context.packageManager).addResolveInfoForIntent(android.content.Intent(android.speech.RecognitionService.SERVICE_INTERFACE),info)
    }
    private fun recognitionResult(text:String)=android.os.Bundle().apply {putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,arrayListOf(text))}
    @Test fun microphoneUsesSelectedLanguageAndOnlyFinalResultsSearch(){
        var searched=""
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {searched=query;return PlaceSearchPage(listOf(destination),emptyList(),false)}}).use {f->
            enableMicrophone(f.context);f.nav.setLanguage("fr-FR");f.nav.listenDestination();f.idle()
            val mic=shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            assertEquals("fr-FR",mic.lastRecognizerIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
            mic.triggerOnPartialResults(recognitionResult("partial"));f.idle();assertEquals("",searched)
            mic.triggerOnResults(recognitionResult("Café Paris"));f.idle();assertEquals("Café Paris",searched);assertFalse(f.nav.state.value.voiceListening);assertTrue(mic.isDestroyed)
        }
    }
    @Test fun microphoneDenialAndRecognitionErrorsDoNotBecomeSearchQueries(){
        var calls=0
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {calls++;return PlaceSearchPage(emptyList(),emptyList(),false)}}).use {f->
            shadowOf(f.context as Application).denyPermissions(Manifest.permission.RECORD_AUDIO)
            f.nav.listenDestination();assertFalse(f.nav.state.value.voiceListening);assertTrue(f.nav.state.value.voiceNotice.contains("권한"));assertEquals(0,calls)
            enableMicrophone(f.context);f.nav.listenDestination();f.idle()
            shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(android.speech.SpeechRecognizer.ERROR_NETWORK)
            f.idle();assertEquals(0,calls);assertFalse(f.nav.state.value.voiceListening);assertTrue(f.nav.state.value.voiceNotice.contains("서버"))
        }
    }
    @Test fun lateMicrophoneResultAfterBackgroundDoesNotSearch(){
        var calls=0
        Fixture(object:NavigationService(){override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>):PlaceSearchPage {calls++;return PlaceSearchPage(emptyList(),emptyList(),false)}}).use {f->
            enableMicrophone(f.context);f.nav.listenDestination();f.idle();val mic=shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            f.nav.setForeground(false);mic.triggerOnResults(recognitionResult("retired cafe"));f.idle()
            assertEquals(0,calls);assertFalse(f.nav.state.value.voiceListening);assertTrue(mic.isDestroyed)
        }
    }
    @Test fun voiceCanSelectAndStartButCannotChangeDestinationOrLanguageWhileDriving(){
        Fixture(object:NavigationService(){
            override suspend fun search(query:String,origin:NavPoint?,nearby:Boolean,excluded:List<String>)=PlaceSearchPage(listOf(destination),emptyList(),false)
            override suspend fun route(origin:NavPoint,destination:NavPlace)=route()
        }).use {f->
            f.nav.locate();f.gps();f.nav.handleVoice("Paris Cafe");f.idle();assertEquals(1,f.nav.state.value.places.size)
            f.nav.handleVoice("1번");f.idle();assertNotNull(f.nav.state.value.guidance.route)
            f.nav.handleVoice("안내 시작");assertTrue(f.nav.state.value.guidance.active)
            f.nav.handleVoice("New Cafe");assertTrue(f.nav.state.value.voiceNotice.contains("주차"));assertTrue(f.nav.state.value.guidance.active)
            val before=f.nav.state.value.language;f.nav.setLanguage("de-DE");assertEquals(before,f.nav.state.value.language)
            f.nav.handleVoice("안내 종료");assertFalse(f.nav.state.value.guidance.active);assertFalse(f.engine.state.value.parkedAllowed)
        }
    }
    @Test fun changingLanguageRelocalizesPreviewAndPersistsOnlyLanguage(){
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate();f.gps();f.nav.plan(destination);f.idle();f.nav.setLanguage("fr-FR")
            assertEquals("fr",f.nav.state.value.guidance.route!!.language);assertFalse(f.nav.state.value.guidance.nextInstruction.contains("출발"))
            assertEquals("fr-FR",RuntimePreferences(f.context).navigationLanguage)
            f.nav.setLanguage("hi-IN");assertEquals("en",f.nav.state.value.guidance.route!!.language);assertTrue(f.nav.state.value.voiceNotice.contains("영어"))
            f.nav.clear();assertEquals("ko-KR",RuntimePreferences(f.context).navigationLanguage)
        }
    }
    @Test fun microphoneTimeoutStopsRecordingWithoutInterpretingAnErrorAsDestination(){
        Fixture(NavigationService()).use {f->
            enableMicrophone(f.context);f.nav.listenDestination();f.idle()
            val mic=shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31));assertFalse(f.nav.state.value.voiceListening);assertTrue(mic.isDestroyed)
            assertTrue(f.nav.state.value.voiceNotice.contains("시간"));assertTrue(f.nav.state.value.places.isEmpty())
        }
    }

    @Test fun accurateCorrectionAtTheSameTimeAsARejectedFixCanRecover() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps();f.advance(1000)
            f.gps(accuracy=500f)
            assertEquals(GpsConnection.POOR,f.nav.state.value.gpsConnection)
            f.gps(accuracy=5f)
            assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
            assertEquals(f.now,f.nav.state.value.mapFix?.timestampMs)
            f.gps(accuracy=500f,at=f.now-500)
            assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
        }
    }
    @Test fun rerouteRetryExplainsOfflineAndMissingGpsInsteadOfDoingNothing() {
        Fixture(object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route()}).use {f->
            f.nav.locate();f.gps();f.nav.navigateTo(destination);f.idle()
            assertTrue(f.nav.state.value.guidance.active)
            f.engine.setConnectivity(network=false)
            f.nav.retry()
            assertTrue(f.nav.state.value.notice,f.nav.state.value.notice.contains("인터넷"))
            f.engine.setConnectivity(network=true)
            f.advance(11_000);f.nav.tick();f.nav.retry()
            assertTrue(f.nav.state.value.notice,f.nav.state.value.notice.contains("GPS"))
        }
    }
    @Test fun disablingUnusedNetworkProviderDoesNotInvalidateFreshGps() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps()
            val listener=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            val fix=f.nav.state.value.mapFix
            listener.onProviderDisabled(LocationManager.NETWORK_PROVIDER)
            assertEquals(GpsConnection.READY,f.nav.state.value.gpsConnection)
            assertEquals(fix,f.nav.state.value.mapFix)
            listener.onProviderDisabled(LocationManager.GPS_PROVIDER)
            assertEquals(GpsConnection.DISABLED,f.nav.state.value.gpsConnection)
            assertNull(f.nav.state.value.mapFix)
        }
    }
    @Test fun disablingAnotherProviderDoesNotPreserveAnExpiredFix() {
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps();f.advance(10_001)
            val listener=shadowOf(f.manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).first()
            listener.onProviderDisabled(LocationManager.NETWORK_PROVIDER)
            assertNull(f.nav.state.value.mapFix)
        }
    }
    @Test fun freshGpsIsPublishedForTheMapBeforeAnyRouteAndRemovedOnSignalLoss(){
        Fixture(NavigationService()).use {f->
            f.nav.locate();f.gps();assertEquals(origin,f.nav.state.value.mapFix!!.point)
            assertNull(f.nav.state.value.guidance.route);assertNull(f.nav.state.value.guidance.fix)
            f.advance(20);f.gps(accuracy=200f);assertNull(f.nav.state.value.mapFix)
            f.advance(20);f.gps();assertNotNull(f.nav.state.value.mapFix)
            f.nav.setForeground(false);assertNull(f.nav.state.value.mapFix)
        }
    }

}
