package io.cabinos.one.platform

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class NavigationSpeechVoiceTest {
    private fun voice(name:String,tag:String,quality:Int,network:Boolean=false,features:Set<String> = emptySet())=
        Voice(name,Locale.forLanguageTag(tag),quality,Voice.LATENCY_NORMAL,network,features)
    @Test fun highestQualityInstalledOfflineVoiceWinsAcrossLanguages() {
        for(tag in listOf("ko-KR","en-US","ja-JP","fr-FR","de-DE","es-ES","ar-SA")) {
            val low=voice("low",tag,Voice.QUALITY_LOW)
            val high=voice("high",tag,Voice.QUALITY_HIGH)
            val cloud=voice("cloud",tag,Voice.QUALITY_VERY_HIGH,true)
            val missing=voice("missing",tag,Voice.QUALITY_VERY_HIGH,features=setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
            assertEquals(tag,high,NavigationSpeechVoice.select(setOf(low,cloud,missing,high),Locale.forLanguageTag(tag)))
        }
    }
    @Test fun equallyQualifiedVoicesPreserveTheEnginePreferredVoice() {
        val preferred=voice("z-preferred","ko-KR",Voice.QUALITY_HIGH)
        assertEquals(preferred,NavigationSpeechVoice.select(setOf(voice("a-other","ko-KR",Voice.QUALITY_HIGH),preferred),Locale.KOREA,preferred))
    }
    @Test fun requestedAccentAndScriptArePreserved() {
        val canadian=voice("ca","fr-CA",Voice.QUALITY_NORMAL)
        assertEquals(canadian,NavigationSpeechVoice.select(setOf(canadian,voice("fr","fr-FR",Voice.QUALITY_HIGH)),Locale.CANADA_FRENCH))
        assertNull(NavigationSpeechVoice.select(setOf(voice("simplified","zh-Hans-CN",Voice.QUALITY_HIGH)),Locale.forLanguageTag("zh-Hant-TW")))
    }
    @Test fun missingOfflineLanguageIsNotReplacedWithAnotherLanguage() {
        assertNull(NavigationSpeechVoice.select(setOf(voice("en","en-US",Voice.QUALITY_HIGH),voice("ko-cloud","ko-KR",Voice.QUALITY_HIGH,true)),Locale.KOREA))
    }
    @Test fun spokenDistanceAvoidsUnhelpfulPrecisionWithoutChangingCloseTurnDistances() {
        assertEquals(1100,NavigationSpeechVoice.roundedMeters(1079.0))
        assertEquals(1100,NavigationSpeechVoice.roundedMeters(1081.0))
        assertEquals(280,NavigationSpeechVoice.roundedMeters(278.0))
        assertEquals(37,NavigationSpeechVoice.roundedMeters(37.0))
    }
    @Test fun longDistancesUseKilometersInTheRequestedLanguage() {
        assertEquals("일 점 일 킬로미터",NavigationSpeechVoice.distance(1079.0,"ko-KR"))
        assertEquals("이 킬로미터",NavigationSpeechVoice.distance(2000.0,"ko-KR"))
        assertEquals("삼백 미터",NavigationSpeechVoice.distance(300.0,"ko-KR"))
        for(tag in listOf("en-US","fr-FR","de-DE","ja-JP","es-ES")) {
            val text=NavigationSpeechVoice.distance(1200.0,tag)
            assertFalse(tag,text.contains("1200"))
            assertTrue(tag,text.isNotBlank())
        }
    }
    @Test fun frenchAndJapaneseDistancesAvoidAmbiguousNumericSynthesis() {
        assertEquals("trois mille",SpokenDistanceNumbers.french(3000))
        assertEquals("deux cent mille",SpokenDistanceNumbers.french(200000))
        assertEquals("soixante et onze",SpokenDistanceNumbers.french(71))
        assertEquals("はっせん",SpokenDistanceNumbers.japanese(8000))
        assertEquals("trois cents mètres",NavigationSpeechVoice.distance(300.0,"fr-FR"))
        assertEquals("soixante mètres",NavigationSpeechVoice.distance(60.0,"fr-FR"))
        assertEquals("quatre-vingt-dix mètres",NavigationSpeechVoice.distance(90.0,"fr-FR"))
        assertEquals("un virgule deux kilomètre",NavigationSpeechVoice.distance(1200.0,"fr-FR"))
        assertEquals("さんびゃくメートル",NavigationSpeechVoice.distance(300.0,"ja-JP"))
        assertEquals("ろっぴゃくメートル",NavigationSpeechVoice.distance(600.0,"ja-JP"))
        assertEquals("はっぴゃくメートル",NavigationSpeechVoice.distance(800.0,"ja-JP"))
        assertEquals("いちてんにキロメートル",NavigationSpeechVoice.distance(1200.0,"ja-JP"))
    }
    @Test fun koreanDistanceIsReadAsWordsInsteadOfDigitsOrSeparators() {
        assertEquals("천백 미터",NavigationSpeechVoice.koreanMeters(1100))
        assertEquals("삼백 미터",NavigationSpeechVoice.koreanMeters(300))
    }
}
