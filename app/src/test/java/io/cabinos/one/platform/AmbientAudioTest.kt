package io.cabinos.one.platform

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class AmbientAudioTest {
    @Test fun staticPcmLoadsBeforePlaybackReadinessIsRequired() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val audio=AmbientAudio(context)
        try {val played=audio.play("백색 소음");if(!played)throw AssertionError(audio.status.value,org.robolectric.shadows.ShadowLog.getLogsForTag("CabinAudio").lastOrNull()?.throwable);assertTrue(audio.playing)}finally{audio.stop()}
        assertFalse(audio.playing)
    }
}
