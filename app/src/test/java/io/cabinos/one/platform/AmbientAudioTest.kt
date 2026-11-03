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
    @Test fun automaticSessionStopDoesNotKeepAPlayingStatus() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val audio=AmbientAudio(context)
        assertTrue(audio.play("백색 소음"))
        audio.stop()
        assertFalse(audio.playing)
        assertEquals("배경 소리 꺼짐",audio.status.value)
    }
    @Test fun lateFocusLossFromPreviousSoundDoesNotStopNewSound() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=shadowOf(context.getSystemService(AudioManager::class.java))
        manager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val audio=AmbientAudio(context)
        try {
            assertTrue(audio.play("백색 소음"))
            val previous=manager.lastAudioFocusRequest.listener
            assertTrue(audio.play("잔잔한 톤"))
            val currentStatus=audio.status.value
            previous.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            assertTrue("이전 재생의 지연 콜백은 새 재생에 영향을 주면 안 됩니다",audio.playing)
            assertEquals(currentStatus,audio.status.value)
            manager.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertFalse(audio.playing)
            assertTrue(audio.status.value.contains("다른 소리"))
        } finally {audio.stop()}
    }
    @Test fun lateFocusLossAfterUserStopDoesNotReplaceStoppedMessage() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val manager=shadowOf(context.getSystemService(AudioManager::class.java))
        manager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val audio=AmbientAudio(context)
        assertTrue(audio.play("백색 소음"))
        val previous=manager.lastAudioFocusRequest.listener
        audio.stopByUser()
        previous.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertFalse(audio.playing)
        assertEquals("배경 소리 꺼짐",audio.status.value)
    }
    @Test fun staticPcmLoadsBeforePlaybackReadinessIsRequired() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val audio=AmbientAudio(context)
        try {val played=audio.play("백색 소음");if(!played)throw AssertionError(audio.status.value,org.robolectric.shadows.ShadowLog.getLogsForTag("CabinAudio").lastOrNull()?.throwable);assertTrue(audio.playing)}finally{audio.stop()}
        assertFalse(audio.playing)
    }
}
