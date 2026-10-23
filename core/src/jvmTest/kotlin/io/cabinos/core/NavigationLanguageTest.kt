package io.cabinos.core
import org.junit.Assert.*
import org.junit.Test

class NavigationLanguageTest {
    private val p=NavPoint(48.85,2.35)
    @Test fun all31PacksHandleEveryDirectionAndExitWithoutUnresolvedTokens(){
        assertEquals(31,NavigationLanguages.codes.size)
        for(code in NavigationLanguages.codes)for(type in listOf("depart","arrive","turn","merge","fork","on ramp","off ramp","end of road","roundabout","rotary","exit roundabout","exit rotary","continue","new name","notification"))for(modifier in listOf("left","right","slight left","slight right","sharp left","sharp right","uturn","straight"))for(exit in listOf(null,1,4,10,14)){
            val text=NavigationLanguages.instruction(NavManeuver(p,type,modifier,"Rue A <B>",exit),code)
            assertTrue("$code $type $modifier",text.isNotBlank());assertFalse(text.contains('{'));assertTrue(text.contains("Rue A <B>"))
        }
    }
    @Test fun regionalResolutionIsExplicitAndUnsupportedLanguagesUseEnglish(){
        assertEquals("fr-CA,en",PlaceSearch.requests("Café Paris",language="fr-CA").first()["accept-language"]);assertEquals("fr",NavigationLanguages.resolve("fr-CA"));assertEquals("pt-BR",NavigationLanguages.resolve("pt-BR"));assertEquals("pt-PT",NavigationLanguages.resolve("pt"))
        assertEquals("zh-Hans",NavigationLanguages.resolve("zh-CN"));assertEquals("en",NavigationLanguages.resolve("zh-Hant"));assertEquals("en",NavigationLanguages.resolve("hi-IN"));assertEquals("no",NavigationLanguages.resolve("nb-NO"))
        assertFalse(NavigationLanguages.validTag("en\r\nHost: bad"));assertFalse(NavigationLanguages.validTag("../ko"));assertTrue(NavigationLanguages.validTag("zh-Hans-CN"))
    }
    @Test fun changingRouteLanguageKeepsGeometryAndLocalizesActualTurns(){
        val end=NavPoint(48.86,2.36)
        val route=NavigationGeometry.route(NavPlace("Shop",end),listOf(p,end),1500.0,300.0,listOf(NavManeuver(p,"turn","right","Road"),NavManeuver(end,"arrive")))
        val french=route.localized("fr-FR")
        assertSame(route.points,french.points);assertTrue(french.steps[0].instruction.contains("droite"));assertTrue(french.localized("de").steps[0].instruction.contains("rechts",true));assertTrue(french.localized("ko").steps[0].instruction.contains("우회전"))
    }
    @Test fun localizedSpeechAndArrivalNeverMixKoreanIntoOtherLanguages(){
        for(code in NavigationLanguages.codes.filter {it!="ko"}){
            val instruction=NavigationLanguages.instruction(NavManeuver(p,"turn","left"),code)
            val text=NavigationLanguages.spoken(code,"250 m",instruction)
            assertTrue(text.contains("250 m"));assertTrue(text.contains(instruction));assertFalse(text.contains('{'))
            assertFalse(NavigationLanguages.arrival(code,60.0).contains("목적지"))
        }
    }
    @Test fun allLanguagesDistinguishVehicleAccessFromArrivalAtTheRequestedBuilding(){
        assertEquals(NavigationLanguages.codes.toSet(),navVehicleAccessLabels.keys)
        for(code in NavigationLanguages.codes) {
            val text=NavigationLanguages.arrival(code,51.9)
            assertTrue("$code: $text",text.startsWith(navVehicleAccessLabels.getValue(code)))
            assertTrue(text.contains("51"));assertFalse(text.contains('{'))
            assertNotEquals(NavigationLanguages.arrival(code,0.0),text)
        }
        assertEquals("Vehicle route endpoint · Destination: ≈ 51 m",NavigationLanguages.arrival("en-NZ",51.9))
        assertEquals(NavigationLanguages.arrival("en",51.9),NavigationLanguages.arrival("hi-IN",51.9))
    }
    @Test fun commandsAreExactAndNamesRetainCase(){
        assertEquals(NavigationVoiceAction.STOP,NavigationVoice.parse("안내 종료!").action)
        assertEquals(NavigationVoiceAction.REPEAT,NavigationVoice.parse("Repeat").action)
        assertEquals(NavigationVoiceAction.START,NavigationVoice.parse("démarrer la navigation").action)
        assertEquals(1,NavigationVoice.parse("2번").index);assertEquals(0,NavigationVoice.parse("one").index)
        assertEquals("Starbucks Paris",NavigationVoice.parse("Navigate to Starbucks Paris").query)
        assertEquals("서울역",NavigationVoice.parse("서울역 길 찾아줘").query)
        assertEquals(NavigationVoiceAction.SEARCH,NavigationVoice.parse("Stop Cafe Paris").action)
    }
}
