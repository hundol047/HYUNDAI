package io.cabinos.one.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@Implements(TextToSpeech::class)
class SpeakingTextToSpeech:ShadowTextToSpeech() {
    var busy=false
    var languageChanges=0
    var lastUtteranceId:String?=null
    @Implementation override fun speak(text:CharSequence,queueMode:Int,params:android.os.Bundle?,utteranceId:String?):Int {lastUtteranceId=utteranceId;return super.speak(text,queueMode,params,utteranceId)}
    var progress:android.speech.tts.UtteranceProgressListener?=null
    @Implementation fun setOnUtteranceProgressListener(listener:android.speech.tts.UtteranceProgressListener):Int {progress=listener;return TextToSpeech.SUCCESS}
    @Implementation fun isSpeaking()=busy
    @Implementation override fun setLanguage(locale:Locale):Int {languageChanges++;return super.setLanguage(locale)}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],shadows=[SpeakingTextToSpeech::class])
class SpeechOutputContinuityTest {
    @Test fun cabinUpdatesAndRepeatedTapsDoNotCutOffAnActiveTurn() {
        val speech=AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
        try {
            ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
            val tts=org.robolectric.shadow.api.Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            assertTrue(speech.speakNavigation("삼백 미터 앞에서 우회전하세요.","ko-KR"))
            tts.busy=true
            speech.speak("충전 시간이 바뀌었습니다.")
            speech.speakNavigation("삼백 미터 앞에서 우회전하세요.","ko-KR")
            assertEquals(1,tts.spokenTextList.size)
            assertEquals(1,tts.languageChanges)
            // A new safety-relevant turn can replace the previous instruction.
            speech.speakNavigation("지금 좌회전하세요.","ko-KR")
            assertEquals(2,tts.spokenTextList.size)
            assertEquals(1,tts.languageChanges)
            tts.progress?.onDone(tts.lastUtteranceId)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            tts.busy=false
            speech.speakNavigation("지금 좌회전하세요.","ko-KR")
            assertEquals(3,tts.spokenTextList.size)
        } finally {speech.close()}
    }
    @Test fun asynchronousSynthesisFailureIsVisibleAndStaleCallbacksCannotOverwriteNewSpeech() {
        val speech=AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
        try {
            ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
            val tts=org.robolectric.shadow.api.Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            speech.speakNavigation("삼백 미터 앞에서 우회전하세요.","ko-KR")
            assertNotNull("실제 재생 실패를 감지할 리스너가 필요합니다",tts.progress)
            val first=tts.lastUtteranceId
            tts.progress!!.onError(first,TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(speech.status,speech.status.contains("음성 재생 실패"))
            speech.speakNavigation("지금 좌회전하세요.","ko-KR")
            val current=speech.status
            tts.progress!!.onError(first,TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(current,speech.status)
        } finally {speech.close()}
    }
    @Test fun queuedSynthesisIsNotRestartedAndTimeoutAllowsAnExplicitRetry() {
        val speech=AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
        try {
            ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
            val tts=org.robolectric.shadow.api.Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            speech.speakNavigation("우회전입니다.","ko-KR")
            assertFalse(tts.busy)
            speech.speakNavigation("우회전입니다.","ko-KR")
            speech.speak("충전 시간이 바뀌었습니다.")
            assertEquals(1,tts.spokenTextList.size)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(8))
            assertTrue(tts.isStopped)
            assertTrue(speech.status.contains("합성이 지연"))
            assertFalse(speech.navigationSpeaking)
            assertTrue(speech.speakNavigation("우회전입니다.","ko-KR"))
            assertEquals(2,tts.spokenTextList.size)
        } finally {speech.close()}
    }
    @Test fun delayedFailureAfterSafetyStopCannotReviveAnUtterance() {
        val speech=AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
        try {
            ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
            val tts=org.robolectric.shadow.api.Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            speech.speakNavigation("우회전입니다.","ko-KR")
            val old=tts.lastUtteranceId
            speech.stopSpeaking()
            val status=speech.status
            tts.progress!!.onError(old,TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(speech.navigationSpeaking)
            assertEquals(status,speech.status)
        } finally {speech.close()}
    }
    @Test fun identicalWordsAtTheNextIntersectionAreNotSuppressedAsDuplicateSpeech() {
        val speech=AndroidSpeechProvider(ApplicationProvider.getApplicationContext<Context>())
        try {
            ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
            val tts=org.robolectric.shadow.api.Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            speech.speakNavigation("잠시 후, 우회전입니다.","ko-KR","turn-1")
            speech.speakNavigation("잠시 후, 우회전입니다.","ko-KR","turn-1")
            assertEquals(1,tts.spokenTextList.size)
            speech.speakNavigation("잠시 후, 우회전입니다.","ko-KR","turn-2")
            assertEquals(2,tts.spokenTextList.size)
        } finally {speech.close()}
    }
    @Test fun asynchronousPlaybackFailureReachesTheNavigationScreen() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val speech=AndroidSpeechProvider(context)
        val nav=LiveNavigation(context,io.cabinos.core.UnifiedVehicleContextEngine(),speech)
        try {
            ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
            val tts=org.robolectric.shadow.api.Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            speech.speakNavigation("우회전입니다.","ko-KR")
            tts.progress!!.onError(tts.lastUtteranceId,TextToSpeech.ERROR_SYNTHESIS)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(nav.state.value.voiceNotice,nav.state.value.voiceNotice.contains("음성 재생 실패"))
        } finally {nav.close();speech.close()}
    }
    @Test fun regionalVoiceIsNotCollapsedToBaseLanguageAndUnsupportedPackUsesEnglish() {
        assertEquals("fr-CA",NavigationSpeechVoice.locale("fr-CA").toLanguageTag())
        assertEquals("en-AU",NavigationSpeechVoice.locale("en-AU").toLanguageTag())
        assertEquals("en",NavigationSpeechVoice.locale("hi-IN").toLanguageTag())
    }
}
