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
class SimpleSessionControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val paused = CabinSession("test", SessionKind.FOCUS, 0, 600, 500, SessionStatus.PAUSED)

    @Test
    fun expiredSessionExplainsDisabledResumeButAllowsEnding() {
        var ended = false
        compose.runOnIdle {
            compose.activity.setContent {
                CabinTheme {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        SessionActionButtons(
                            AppState(parkedAllowed = true, session = paused),
                            {},
                            { ended = true },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("계속").assertIsNotEnabled().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("다시 시작할 여유가 없어요. 출발 준비 또는 시간 설정을 확인하세요.").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsEnabled().assertHeightIsAtLeast(56.dp).performClick()
        compose.runOnIdle { assertTrue(ended) }
    }

    @Test
    fun oneTapMuteDoesNotOpenSoundPickerEvenWhenPaused() {
        var stopped = 0
        var opened = 0
        compose.runOnIdle {
            compose.activity.setContent {
                CabinTheme {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        AmbientQuickActions("빗소리", false, { opened++ }, { stopped++ })
                    }
                }
            }
        }
        compose.onNodeWithText("빗소리 · 소리 설정").assertIsNotEnabled()
        compose
            .onNodeWithText("소리 끄기")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp)
            .performClick()
        compose.runOnIdle {
            assertEquals(1, stopped)
            assertEquals(0, opened)
        }
    }

    @Test
    fun unsafeVehicleDisablesBothSessionActions() {
        compose.runOnIdle {
            compose.activity.setContent {
                CabinTheme {
                    SessionActionButtons(
                        AppState(
                            session = paused,
                            schedule = SharedSchedule(availableSeconds = 600),
                        ),
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("계속").assertIsNotEnabled()
        compose.onNodeWithText("종료").assertIsNotEnabled()
    }
}
