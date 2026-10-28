package io.cabinos.one.platform

import android.content.*
import android.content.pm.*
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=android.app.Application::class)
class VoiceAvailabilityTest {
    @Test fun missingTtsOrRecognizerDisablesVoiceEntry() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val recognizer=ResolveInfo().apply { serviceInfo=ServiceInfo().apply { packageName="test.recognizer";name="TestRecognizer";enabled=true;exported=true } }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent("android.speech.RecognitionService"),recognizer)
        assertFalse(voiceServicesAvailable(context))
        val service=ResolveInfo().apply { serviceInfo=ServiceInfo().apply { packageName="test.tts";name="TestTts";enabled=true;exported=true } }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent("android.intent.action.TTS_SERVICE"),service)
        assertTrue(voiceServicesAvailable(context))
        shadowOf(context.packageManager).removeResolveInfosForIntent(Intent("android.speech.RecognitionService"),"test.recognizer")
        assertFalse(voiceServicesAvailable(context))
    }
    @Test fun speechInputRemainsAvailableWithoutTtsOutputEngine() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val recognizer=ResolveInfo().apply {serviceInfo=ServiceInfo().apply {packageName="test.recognizer";name="TestRecognizer";enabled=true;exported=true}}
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent("android.speech.RecognitionService"),recognizer)
        val speech=AndroidSpeechProvider(context)
        try {speech.refreshVoiceAvailability();assertTrue(speech.voiceAvailable.value)}finally{speech.close()}
    }

}
