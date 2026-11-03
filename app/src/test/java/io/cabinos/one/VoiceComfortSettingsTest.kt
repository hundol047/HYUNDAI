package io.cabinos.one

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.cabinos.one.platform.AndroidSpeechProvider
import io.cabinos.one.platform.SpeakingTextToSpeech
import io.cabinos.one.ui.CabinTheme
import io.cabinos.one.ui.VoiceComfortSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi",shadows=[SpeakingTextToSpeech::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceComfortSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun safetyLockDismissesChooserWithoutReopeningAfterParking() {
        val enabled=mutableStateOf(true)
        val speech=AndroidSpeechProvider(compose.activity)
        try {
            compose.runOnIdle {compose.activity.setContent {CabinTheme {Column {VoiceComfortSettings(speech,"ko-KR",enabled.value)}}}}
            compose.onNodeWithText("목소리 골라 듣기").performClick()
            compose.onNodeWithText("편안한 목소리를 골라 주세요").assertIsDisplayed()
            compose.runOnIdle {enabled.value=false}
            compose.onNodeWithText("편안한 목소리를 골라 주세요").assertDoesNotExist()
            compose.runOnIdle {enabled.value=true}
            compose.onNodeWithText("편안한 목소리를 골라 주세요").assertDoesNotExist()
        } finally {speech.close()}
    }
    @Test fun engineReadyRefreshesAnAlreadyOpenVoiceList() {
        val speech=AndroidSpeechProvider(compose.activity)
        val tts=Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
        try {
            compose.runOnIdle {compose.activity.setContent {CabinTheme {Column {VoiceComfortSettings(speech,"ko-KR",true)}}}}
            compose.onNodeWithText("목소리 골라 듣기").performClick()
            compose.onNodeWithText("자동 선택").assertIsNotEnabled()
            compose.runOnIdle {
                ShadowTextToSpeech.addVoice(Voice("installed",Locale.KOREA,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,emptySet()))
                tts.onInitListener.onInit(TextToSpeech.SUCCESS)
            }
            compose.onNodeWithText("자동 선택").assertIsEnabled()
            compose.onNodeWithText("목소리 1",substring=true).assertIsDisplayed()
        } finally {speech.close()}
    }
    @Test fun initializationFailureIsShownInsteadOfAnEndlessPreparingMessage() {
        val speech=AndroidSpeechProvider(compose.activity)
        val tts=Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
        try {
            compose.runOnIdle {compose.activity.setContent {CabinTheme {Column {VoiceComfortSettings(speech,"ko-KR",true)}}}}
            compose.onNodeWithText("목소리 골라 듣기").performClick()
            compose.runOnIdle {tts.onInitListener.onInit(TextToSpeech.ERROR)}
            compose.onNodeWithText("TTS 사용 불가",substring=true).assertIsDisplayed()
            compose.onNodeWithText("자동 선택").assertIsNotEnabled()
            compose.onNodeWithText("음성 엔진 다시 연결").performClick()
            val replacement=Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
            assertNotSame(tts,replacement)
            compose.runOnIdle {replacement.onInitListener.onInit(TextToSpeech.SUCCESS)}
            compose.onNodeWithText("자동 선택").assertIsEnabled()
            compose.onNodeWithText("음성 엔진 다시 연결").assertDoesNotExist()
        } finally {speech.close()}
    }
    @Test fun closingChooserStopsItsPreview() {
        val speech=AndroidSpeechProvider(compose.activity)
        val tts=Shadow.extract<SpeakingTextToSpeech>(ShadowTextToSpeech.getLastTextToSpeechInstance())
        try {
            compose.runOnIdle {
                ShadowTextToSpeech.addLanguageAvailability(Locale.KOREA)
                tts.onInitListener.onInit(TextToSpeech.SUCCESS)
                compose.activity.setContent {CabinTheme {Column {VoiceComfortSettings(speech,"ko-KR",true)}}}
            }
            compose.onNodeWithText("목소리 골라 듣기").performClick()
            compose.onNodeWithText("자동 선택").performClick()
            compose.runOnIdle {assertTrue(speech.navigationSpeaking)}
            compose.onNodeWithText("완료").performClick()
            compose.runOnIdle {assertFalse(speech.navigationSpeaking)}
        } finally {speech.close()}
    }
}
