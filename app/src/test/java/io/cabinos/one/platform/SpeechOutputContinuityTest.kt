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
            tts.busy=false
            speech.speakNavigation("지금 좌회전하세요.","ko-KR")
            assertEquals(3,tts.spokenTextList.size)
        } finally {speech.close()}
    }
    @Test fun regionalVoiceIsNotCollapsedToBaseLanguageAndUnsupportedPackUsesEnglish() {
        assertEquals("fr-CA",NavigationSpeechVoice.locale("fr-CA").toLanguageTag())
        assertEquals("en-AU",NavigationSpeechVoice.locale("en-AU").toLanguageTag())
        assertEquals("en",NavigationSpeechVoice.locale("hi-IN").toLanguageTag())
    }
}
