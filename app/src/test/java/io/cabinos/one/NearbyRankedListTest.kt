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
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NearbyRankedListTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun rankedPlacesScrollToLastItemAndSelectWithoutInventingPhotographs() {
        val base=DemoActivityRepository().activities().first()
        val options=(1..15).map {ActivityOption(base.copy(id="$it",name="장소 $it",simulated=false),1200,true)}
        var selected=""
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NearbyRankedList(options,null,false,Modifier.fillMaxWidth().height(440.dp)){selected=it}
        }}}
        compose.onNodeWithText("장소 1").assertIsDisplayed()
        compose.onAllNodesWithText("등록 사진 없음").onFirst().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        compose.onNodeWithText("장소 15").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("15",selected)}
    }
}
