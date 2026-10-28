package io.cabinos.one

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.cabinos.one.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParkedUxTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun focusPresetsAreAccessibleAndDisabledOutsideParking() {
        var added=""
        fun show(parked:Boolean) { compose.runOnIdle { compose.activity.setContent { CabinTheme { FocusTaskPresets(parked) { added=it } } } } }
        show(true)
        compose.onNodeWithText("메일 확인").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("메일 확인",added)
        show(false);compose.onNodeWithText("메일 확인").assertIsNotEnabled()
    }
    @Test fun narrowFocusPresetsLeaveSpaceForTasksAndAllCommandsRemainReachable() {
        var added=""
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            Box(Modifier.width(240.dp)) {FocusTaskPresets(true) {added=it}}
        }}}
        compose.onNodeWithTag("focus-presets").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("스트레칭").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("스트레칭",added)
        compose.onNodeWithText("메일 확인").performScrollTo().performClick()
        assertEquals("메일 확인",added)
    }
    @Test fun arrivalWithoutRouteExplainsDisabledControls() {
        compose.runOnIdle { compose.activity.setContent { CabinTheme { ArrivalClockControls(10000,false) {} } } }
        compose.onNodeWithText("차량 내비에서 목적지를 먼저 설정하세요").assertIsDisplayed()
        compose.onNodeWithText("도착 시각 적용").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
    }
    @Test fun missingVoiceServicesShowCommandsWithoutMicrophone() {
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.runOnIdle { compose.activity.setContent { CabinTheme { AssistantDialog(io.cabinos.core.AppState(parkedAllowed=true),vm,{}, {}) } } }
        compose.onNodeWithText("말하기").assertDoesNotExist()
        compose.onNodeWithText("25분 집중 모드 켜줘").assertIsDisplayed()
    }
    @Test fun sharedRelaxActionExplainsInsufficientTimeAndEnablesAtOneMinute() {
        var starts=0
        fun show(seconds:Long) {compose.runOnIdle {compose.activity.setContent {CabinTheme {
            CabinAdvicePanel(io.cabinos.core.AppState(parkedAllowed=true,
                schedule=io.cabinos.core.SharedSchedule(availableSeconds=seconds)),
                io.cabinos.one.platform.CabinAdvice(passengerDetected=true)) {starts++}
        }}}}
        show(59)
        compose.onNodeWithText("함께 휴식").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("휴식을 시작하려면 출발 준비 시간을 제외하고 최소 1분이 필요합니다.").assertIsDisplayed()
        show(60)
        compose.onNodeWithText("함께 휴식").assertIsEnabled().performClick()
        assertEquals(1,starts)
    }
    @Test fun cabinAdviceIsVisibleOnlyAfterParkingConfirmation() {
        val advice=io.cabinos.one.platform.CabinAdvice(true,listOf("창문이 열려 있어요"),listOf("워셔액 부족 경고를 확인하세요"))
        fun show(parked:Boolean) { compose.runOnIdle { compose.activity.setContent { CabinTheme {
            CabinAdvicePanel(io.cabinos.core.AppState(parkedAllowed=parked),advice) {}
        } } } }
        show(true)
        compose.onNodeWithText("동승자 감지").assertIsDisplayed()
        compose.onNodeWithText("창문이 열려 있어요").assertIsDisplayed()
        compose.onNodeWithText("워셔액 부족 경고를 확인하세요").assertIsDisplayed()
        show(false);compose.onNodeWithText("동승자 감지").assertDoesNotExist()
        compose.onNodeWithText("창문이 열려 있어요").assertDoesNotExist()
    }
}
