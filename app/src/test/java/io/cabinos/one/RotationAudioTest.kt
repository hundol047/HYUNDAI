package io.cabinos.one

import android.media.AudioManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.cabinos.core.*
import io.cabinos.one.ui.CabinViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class)
class RotationAudioTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun configurationRecreationKeepsAudioAndSessionButBackgroundStopsThem() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20000){vm.state.value.sample!=null}
        compose.runOnIdle {vm.selectScenario("충전")}
        Thread.sleep(1600)
        compose.runOnIdle {
            vm.engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,charging=true,chargeRemainingSeconds=2400,timestampMs=System.currentTimeMillis()))
            vm.waiting(40);vm.preferences(vm.state.value.preferences.copy(voiceEnabled=false));vm.start(SessionKind.FOCUS,25)
            assertTrue(vm.state.value.parkedAllowed);assertEquals(SessionStatus.RUNNING,vm.state.value.session?.status)
            shadowOf(compose.activity.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            vm.toggleAudio("빗소리")
        }
        compose.waitUntil(10000){shadowOf(android.os.Looper.getMainLooper()).idle();vm.audioPreset.value=="빗소리"}
        repeat(2){
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                assertSame(vm,ViewModelProvider(compose.activity)[CabinViewModel::class.java])
                assertEquals("빗소리",vm.audioPreset.value)
                assertEquals(SessionStatus.RUNNING,vm.state.value.session?.status)
            }
        }
        compose.runOnIdle {vm.setForeground(false)}
        assertNull(vm.audioPreset.value)
        assertEquals(SessionStatus.PAUSED,vm.state.value.session?.status)
    }
}
