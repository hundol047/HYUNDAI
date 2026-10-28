package io.cabinos.core

import org.junit.Assert.*
import org.junit.Test

class NavigationSpeechTest {
    private fun turn(type:String="turn",modifier:String="right",road:String="",exit:Int?=null)=
        NavManeuver(NavPoint(0.0,0.0),type,modifier,road,exit)

    @Test fun politeDirectionsKeepDistanceAndDropLongRoadNamesNearTurns() {
        assertEquals("약 삼백 미터 앞, 한강대로 방면 우회전입니다.",
            NavigationSpeech.guidance(turn(road="한강대로"),"ko","삼백 미터"))
        assertEquals("잠시 후, 우회전입니다.",NavigationSpeech.guidance(turn(road="긴 도로명"),"ko","십 미터",true))
    }
    @Test fun mergingAndExitingDoNotAskForAnUnnecessaryTurnOrInventALane() {
        assertEquals("약 삼백 미터 앞, 왼쪽 도로로 합류하는 구간입니다.",NavigationSpeech.guidance(turn("merge","left"),"ko","삼백 미터"))
        assertEquals("약 삼백 미터 앞, 오른쪽 출구로 나가는 구간입니다.",NavigationSpeech.guidance(turn("off ramp"),"ko","삼백 미터"))
        assertEquals("약 삼백 미터 앞, 갈림길에서 왼쪽 길로 진행하는 구간입니다.",NavigationSpeech.guidance(turn("fork","slight left"),"ko","삼백 미터"))
    }
    @Test fun roundaboutExitUsesSpokenOrdinalAndUnknownExitIsNotInvented() {
        assertEquals("약 삼백 미터 앞, 회전교차로의 세 번째 출구입니다.",NavigationSpeech.guidance(turn("roundabout",exit=3),"ko","삼백 미터"))
        assertEquals("약 삼백 미터 앞, 회전교차로 진입 구간입니다.",NavigationSpeech.guidance(turn("roundabout"),"ko","삼백 미터"))
    }
    @Test fun allKoreanManeuversUseFormalDeclarativeEndings() {
        for(type in listOf("turn","continue","new name","arrive","depart","merge","fork","on ramp","off ramp","roundabout","rotary","exit roundabout","exit rotary","end of road"))
            for(modifier in listOf("left","right","uturn","slight left","slight right","sharp left","sharp right","straight"))
                for(near in listOf(true,false)) {
                    val speech=NavigationSpeech.guidance(turn(type,modifier,"한강대로",3),"ko","삼백 미터",near)
                    assertTrue(speech,speech.split('.').filter {it.isNotBlank()}.all {it.trim().endsWith("입니다")})
                }
        assertEquals("목적지까지 약 삼백 미터 거리입니다.",NavigationSpeech.guidance(turn("arrive"),"ko","삼백 미터"))
    }
    @Test fun frenchSpeechAddressesTheDriverWhileDisplayLabelIsPreserved() {
        val m=turn()
        assertTrue(NavigationLanguages.instruction(m,"fr").contains("Tourner"))
        assertEquals("Dans 300 mètres, Tournez à droite.",NavigationSpeech.guidance(m,"fr","300 mètres"))
    }
    @Test fun approachingTheDestinationNeverAnnouncesCompletedArrival() {
        assertEquals("In 300 meters, you will reach the destination area.",NavigationSpeech.guidance(turn("arrive"),"en","300 meters"))
        assertEquals("300メートル先、目的地付近です。",NavigationSpeech.guidance(turn("arrive"),"ja","300メートル"))
        assertEquals("목적지 부근입니다. 주변 확인이 필요한 구간입니다.",NavigationSpeech.guidance(turn("arrive"),"ko","십 미터",true))
    }
    @Test fun japaneseUsesAnUnambiguousSpokenTurnInsteadOfACompactLabel() {
        assertEquals("さんびゃくメートル先、右に曲がります。",NavigationSpeech.guidance(turn(),"ja","さんびゃくメートル"))
        assertEquals("さんびゃくメートル先、左に曲がります。",NavigationSpeech.guidance(turn(modifier="left"),"ja","さんびゃくメートル"))
    }
    @Test fun everyPackAndManeuverHasSpeechWithoutVisualSeparatorsOrUnexpandedTokens() {
        for(code in NavigationLanguages.codes)for(type in listOf("turn","arrive","depart","merge","fork","roundabout","on ramp","off ramp")) {
            val result=NavigationSpeech.guidance(turn(type,road="Road A · City B\n",exit=3),code,"300 m")
            assertTrue("$code/$type",result.isNotBlank())
            assertFalse(result,result.contains(" · ")||result.contains('{')||result.contains('\n'))
        }
    }
}
