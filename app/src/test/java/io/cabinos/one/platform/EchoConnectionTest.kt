package io.cabinos.one.platform

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class EchoConnectionTest {
    private fun parked(block:(LiveNavigation,CompletableDeferred<Boolean>)->Unit) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        var now=System.currentTimeMillis()
        val engine=UnifiedVehicleContextEngine({now})
        engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        now+=1600;engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        val result=CompletableDeferred<Boolean>()
        val probe=object:EchoRoadClient(){override suspend fun verify()=result.await()}
        val speech=AndroidSpeechProvider(context)
        val nav=LiveNavigation(context,engine,speech,clock={now},echoProbe=probe)
        try {block(nav,result)}finally{nav.close();speech.close()}
    }
    @Test fun parkedConnectionIsCheckedWithoutGpsAndTickDoesNotCancelIt()=parked {nav,result ->
        nav.configureEcho("https://example.org","a".repeat(32))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(nav.state.value.echoChecking);assertFalse(nav.state.value.echoConnected)
        nav.tick();result.complete(false);shadowOf(Looper.getMainLooper()).idle()
        assertTrue(nav.state.value.echoConnected);assertFalse(nav.state.value.echoChecking)
        assertTrue(nav.state.value.echoNotice.contains("인증 성공"))
        assertTrue(nav.state.value.echoNotice.contains("미적재"))
        assertNull(nav.state.value.mapFix)
    }
    @Test fun unauthorizedKeyReportsFailureInsteadOfSuccess()=parked {nav,result ->
        nav.configureEcho("https://example.org","a".repeat(32))
        shadowOf(Looper.getMainLooper()).idle()
        result.completeExceptionally(EchoHttpException(401));shadowOf(Looper.getMainLooper()).idle()
        assertFalse(nav.state.value.echoConnected);assertFalse(nav.state.value.echoChecking)
        assertTrue(nav.state.value.echoNotice.contains("접근키 인증 실패"))
    }
    @Test fun backgroundCannotCompletePendingAuthentication()=parked {nav,result ->
        nav.configureEcho("https://example.org","a".repeat(32))
        shadowOf(Looper.getMainLooper()).idle();nav.setForeground(false)
        result.complete(false);shadowOf(Looper.getMainLooper()).idle()
        assertFalse(nav.state.value.echoChecking);assertFalse(nav.state.value.echoConnected)
    }
}
