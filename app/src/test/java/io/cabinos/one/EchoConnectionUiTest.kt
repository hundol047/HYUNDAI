package io.cabinos.one

import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.cabinos.core.*
import io.cabinos.one.platform.*
import io.cabinos.one.ui.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoConnectionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun connectKeepsDialogOpenAndShowsAuthenticatedResult() {
        var now=System.currentTimeMillis()
        val engine=UnifiedVehicleContextEngine({now})
        engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        now+=1600;engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=now))
        val answer=CompletableDeferred<Boolean>()
        val probe=object:EchoRoadClient(){override suspend fun verify()=answer.await()}
        val speech=AndroidSpeechProvider(compose.activity)
        val nav=LiveNavigation(compose.activity,engine,speech,clock={now},echoProbe=probe)
        try {
            compose.activity.setContent {
                val ui by nav.state.collectAsState()
                CabinTheme { EchoRoadPanel(ui,nav,true) }
            }
            compose.onNodeWithText("EchoRoad 연결").performClick()
            compose.onNodeWithText("기기 접근키").performTextInput("a".repeat(32))
            compose.onNodeWithText("연결 확인").performClick()
            compose.onNodeWithText("EchoRoad 위험 정보").assertIsDisplayed()
            compose.onNodeWithText("확인 중…").assertIsDisplayed()
            compose.runOnIdle {answer.complete(false)}
            compose.onNodeWithText("EchoRoad 위험 정보").assertIsDisplayed()
            compose.onAllNodesWithText("서버 연결·접근키 인증 성공",substring=true).assertCountEquals(2)
            compose.onNodeWithText("닫기").performClick()
            compose.onNodeWithText("EchoRoad 위험 정보").assertDoesNotExist()
            compose.onNodeWithText("EchoRoad 연결됨").assertIsDisplayed()
        } finally {
            compose.runOnIdle {nav.close();speech.close()}
        }
    }
}
