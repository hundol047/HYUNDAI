package io.cabinos.one.platform

import android.Manifest
import android.location.*
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/** Synthetic route/GPS with the real Android navigation controller and TTS callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NavigationSpeechProgressTest {
    private fun exercise(turns:List<Int>,body:(LiveNavigation,(Int,Long)->Unit,org.robolectric.shadows.ShadowTextToSpeech)->Unit) {
        val context=ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager=context.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        var now=100_000L
        val engine=UnifiedVehicleContextEngine({now})
        fun park(){engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))}
        park();now+=1600;park()
        val points=(0..60).map {NavPoint(37.5+it*.0001,127.0)}
        val destination=NavPlace("회전 안내 검사용 목적지",points.last())
        val route=NavigationGeometry.route(destination,points,667.0,120.0,
            listOf(NavManeuver(points.first(),"depart"))+turns.map {NavManeuver(points[it],"turn","left")}+NavManeuver(points.last(),"arrive"))
        val service=object:NavigationService(){override suspend fun route(origin:NavPoint,destination:NavPlace)=route}
        val speech=AndroidSpeechProvider(context)
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
        val tts=shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        tts.onInitListener.onInit(TextToSpeech.SUCCESS)
        val nav=LiveNavigation(context,engine,speech,service,clock={now})
        try {
            nav.locate()
            val listener=shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).last()
            fun gps(index:Int,elapsed:Long) {
                now+=elapsed
                listener.onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {
                    latitude=points[index].latitude;longitude=points[index].longitude;accuracy=5f;speed=0f;time=now
                })
                shadowOf(Looper.getMainLooper()).idle()
            }
            gps(0,1);nav.plan(destination);shadowOf(Looper.getMainLooper()).idle()
            nav.start();assertTrue(nav.state.value.guidance.active)
            assertTrue(tts.spokenTextList.last().contains("좌회전"))
            body(nav,::gps,tts)
        } finally {nav.close();speech.close()}
    }
    @Test fun consecutiveIdenticalTurnsEachReceiveTheirOwnAnnouncement()=exercise(listOf(5,11)) {nav,gps,tts->
        val before=tts.spokenTextList.size
        gps(8,1000)
        assertEquals("좌회전",nav.state.value.guidance.nextInstruction)
        assertTrue(nav.state.value.guidance.turnMeters<60)
        assertEquals("같은 문구라도 다음 교차로 안내가 빠지면 안 됩니다",before+1,tts.spokenTextList.size)
    }
    @Test fun stationaryBandOscillationDoesNotRepeatAfterCooldownButExplicitRepeatWorks()=exercise(listOf(36)) {nav,gps,tts->
        gps(10,2000) // About 289m: advance from ahead to soon.
        val before=tts.spokenTextList.size
        gps(9,31_000) // About 300m: jitter back to ahead after the old cooldown.
        assertEquals("거리 경계의 위치 흔들림으로 같은 회전을 반복하면 안 됩니다",before,tts.spokenTextList.size)
        nav.handleVoice("안내 반복")
        assertEquals(before+1,tts.spokenTextList.size)
    }
}
