package io.cabinos.one

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.cabinos.core.*
import io.cabinos.one.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = CabinApplication::class, qualifiers = "w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StopPlanPanelTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun render(
        state: AppState,
        waiting: (Int) -> Unit = {},
        start: (SessionKind, Int) -> Unit = { _, _ -> },
    ) {
        compose.runOnIdle {
            compose.activity.setContent {
                CabinTheme {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        StopPlanPanel(state, waiting, start, {}, {})
                    }
                }
            }
        }
    }

    @Test
    fun missingPlanOffersLargeQuickDepartureButtons() {
        var chosen = 0
        render(AppState(parkedAllowed = true), waiting = { chosen = it })
        compose
            .onNodeWithText("30분 뒤 출발")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.runOnIdle { assertEquals(30, chosen) }
    }

    @Test
    fun shortWindowRecommendsRestWithoutUsingPreparationTime() {
        var chosen: Pair<SessionKind, Int>? = null
        render(
            AppState(parkedAllowed = true, schedule = TimeOptimizer.calculate(900000, 0, 300)),
            start = { k, m -> chosen = k to m },
        )
        compose.onNodeWithText("10분 휴식 바로 시작").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(SessionKind.RELAX to 10, chosen) }
    }

    @Test
    fun unsafeVehicleCannotStartRecommendedSession() {
        render(AppState(schedule = TimeOptimizer.calculate(900000, 0, 300)))
        compose.onNodeWithText("10분 휴식 바로 시작").assertIsNotEnabled()
    }

    @Test
    fun selectedPlaceIsNotSilentlyReplacedWithCabinSession() {
        render(
            AppState(
                parkedAllowed = true,
                selectedActivityId = "place",
                schedule = TimeOptimizer.calculate(900000, 0, 300, 120),
            )
        )
        compose.onNodeWithText("차 안에서 보내기로 변경").assertIsDisplayed()
        compose.onNodeWithText("8분 휴식 바로 시작").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    fun vehicleWindowShowsFocusActionAndPlan() {
        render(AppState(parkedAllowed = true, schedule = TimeOptimizer.calculate(2400000, 0, 300)))
        compose.onNodeWithText("25분 집중 바로 시작").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("활동 35분 · 왕복 이동 0분 · 출발 준비 5분").assertIsDisplayed()
    }

    @Test
    fun subMinuteSlackDoesNotEnableFiveMinuteExtension() {
        render(
            AppState(
                parkedAllowed = true,
                schedule = SharedSchedule(departureAtMs = 900000, availableSeconds = 601),
                session = CabinSession("test", SessionKind.FOCUS, 0, 900, 600),
            )
        )
        compose.onNodeWithText("여유 안에서 5분 더").assertIsNotEnabled()
    }
}
