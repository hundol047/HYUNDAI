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
        compose.onAllNodesWithText("실제 장소 사진 미확인").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText(REPRESENTATIVE_PHOTO_LABEL).assertCountEquals(0)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        compose.onNodeWithText("장소 15").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("15",selected)}
    }
    @Test fun changingCategoryReturnsToFirstResultButOrdinaryRecompositionDoesNot() {
        val base=DemoActivityRepository().activities().first()
        val options=(1..15).map {ActivityOption(base.copy(id="$it",name="결과 $it"),1200,true)}
        val category=androidx.compose.runtime.mutableStateOf("전체")
        val busy=androidx.compose.runtime.mutableStateOf(false)
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NearbyRankedList(options,null,false,Modifier.fillMaxWidth().height(440.dp),busy=busy.value,resetKey=category.value){}
        }}}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(14)
        compose.onNodeWithText("결과 15").assertIsDisplayed()
        compose.runOnIdle {busy.value=true}
        compose.onNodeWithText("결과 15").assertIsDisplayed()
        compose.runOnIdle {category.value="카페"}
        compose.onNodeWithText("결과 1").assertIsDisplayed()
    }
    @Test fun busyEmptyListExplainsLoadingRatherThanMissingResults() {
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            NearbyRankedList(emptyList(),null,false,Modifier.fillMaxWidth().height(200.dp),busy=true){}
        }}}
        compose.onNodeWithText("장소를 찾고 있습니다. 완료되면 목록에 표시됩니다.").assertIsDisplayed()
        compose.onNodeWithText("표시할 장소가 없습니다.",substring=true).assertDoesNotExist()
    }
    @Test fun cancelIsVisibleAndLargeWithoutHorizontalScrollingOnNarrowPhone() {
        var cancelled=false
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            Box(Modifier.width(320.dp)){NearbySearchProgress {cancelled=true}}
        }}}
        compose.onNodeWithText("검색 취소").assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
        compose.runOnIdle {assertTrue(cancelled)}
    }
    @Test fun vehicleArrivalButtonsStayVisibleAtLargeFontInNarrowPane() {
        var arrival=0L
        compose.runOnIdle {compose.activity.setContent {CabinTheme {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.5f)) {
                Column(Modifier.width(320.dp)) {ArrivalClockControls(1_000_000,true){arrival=it}}
            }
        }}}
        for(label in listOf("시 −","시 +","분 −","분 +"))compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("120분 후").assertIsDisplayed().performClick()
        compose.onNodeWithText("도착 시각 적용").assertIsDisplayed().performClick()
        compose.runOnIdle {assertTrue(arrival>1_000_000)}
    }
    @Test fun vehicleLayoutKeepsNormalWideViewAndReflowsForLargeFont() {
        assertFalse(compactWindow(1280f,720f,1f))
        assertTrue(compactWindow(1280f,720f,1.5f))
        assertTrue(compactWindow(1024f,480f,1f))
        assertTrue(compactWindow(600f,800f,1f))
    }
    @Test fun bundledCategoryPhotographsDecodeOffline() {
        for(category in listOf("카페","식당","산책","쇼핑")) {
            val bitmap=android.graphics.BitmapFactory.decodeResource(compose.activity.resources,representativePhoto(category).drawable)
            assertNotNull(category,bitmap)
            assertTrue(bitmap.width>=200&&bitmap.height>=200)
            bitmap.recycle()
        }
    }
    @Test fun registeredPhotoTakesPriorityOverRepresentativePhoto() {
        val place=DemoActivityRepository().activities().first()
        val photo=io.cabinos.one.platform.PlacePhoto.Photo(
            android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888),
            "등록 사진 출처","https://commons.wikimedia.org/")
        compose.runOnIdle {compose.activity.setContent {CabinTheme {PlacePhotoFrame(place,photo,false)}}}
        compose.onNodeWithContentDescription("${place.name} 등록 사진").assertIsDisplayed()
        compose.onNodeWithText(REPRESENTATIVE_PHOTO_LABEL).assertDoesNotExist()
        compose.onNodeWithText("등록 사진 출처").assertIsDisplayed()
    }

}
