package io.cabinos.one.platform

import android.Manifest
import android.content.*
import android.content.pm.*
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

/** Framework callback tests; no claim of actual microphone or server speech accuracy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=android.app.Application::class)
class SpeechRecognitionLifecycleTest {
    private fun provider():AndroidSpeechProvider {
        val context=ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val info=ResolveInfo().apply {serviceInfo=ServiceInfo().apply {packageName="test.recognizer";name="TestRecognizer";enabled=true;exported=true}}
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent("android.speech.RecognitionService"),info)
        return AndroidSpeechProvider(context)
    }
    private fun result(text:String,confidence:Float=1f)=Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,arrayListOf(text))
        putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES,floatArrayOf(confidence))
    }
    @Test fun cancelledAndDuplicateResultsNeverExecuteAnotherCommand() {
        val speech=provider();val results=mutableListOf<String>();val errors=mutableListOf<String>()
        try {
            speech.listenNavigation("ko-KR",{results+=it},{errors+=it})
            shadowOf(Looper.getMainLooper()).idle()
            val old=shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            speech.cancelRecognition()
            old.triggerOnResults(result("판교역으로 안내해 줘"))
            assertTrue(results.isEmpty());assertTrue(errors.isEmpty())
            speech.listenNavigation("ko-KR",{results+=it},{errors+=it})
            shadowOf(Looper.getMainLooper()).idle()
            val current=shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            old.triggerOnError(SpeechRecognizer.ERROR_NETWORK)
            current.triggerOnResults(result("서울역"));current.triggerOnResults(result("다른 목적지"))
            assertEquals(listOf("서울역"),results);assertTrue(errors.isEmpty())
        } finally {speech.close()}
    }
    @Test fun uncertainRecognitionShowsRetryWithoutStartingNavigation() {
        val speech=provider();val results=mutableListOf<String>();val errors=mutableListOf<String>()
        try {
            speech.listenNavigation("ko-KR",{results+=it},{errors+=it})
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(result("서울역",.2f))
            assertTrue(results.isEmpty());assertEquals(1,errors.size);assertTrue(errors.single().contains("확실히"))
        } finally {speech.close()}
    }
    @Test fun networkFailureReleasesRecognizerAndAllowsExplicitRetry() {
        val speech=provider();val errors=mutableListOf<String>()
        try {
            speech.listenNavigation("ko-KR",{}, {errors+=it});shadowOf(Looper.getMainLooper()).idle()
            val previous=ShadowSpeechRecognizer.getLatestSpeechRecognizer()
            shadowOf(previous).triggerOnError(SpeechRecognizer.ERROR_NETWORK)
            assertTrue(shadowOf(previous).isDestroyed);assertEquals(1,errors.size)
            speech.listenNavigation("ko-KR",{}, {errors+=it});shadowOf(Looper.getMainLooper()).idle()
            assertNotSame(previous,ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        } finally {speech.close()}
    }
}
