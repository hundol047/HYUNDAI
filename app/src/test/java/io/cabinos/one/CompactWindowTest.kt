package io.cabinos.one

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.cabinos.core.*
import io.cabinos.one.ui.CabinViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real Compose measure/draw at narrow widths; synthetic vehicle signals, not an emulator recording. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactWindowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun ready(): CabinViewModel {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000){shadowOf(Looper.getMainLooper()).idle();vm.state.value.sample!=null}
        compose.runOnIdle {vm.selectScenario("충전")}
        Thread.sleep(1600)
        compose.runOnIdle {vm.engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,charging=true,chargeRemainingSeconds=2400,timestampMs=System.currentTimeMillis()));vm.waiting(40)}
        assertTrue(vm.state.value.parkedAllowed)
        return vm
    }
    private fun capture(name:String) {compose.runOnIdle {
        val view=compose.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file=File("build/screenshots/$name.png");file.parentFile.mkdirs()
        file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }}
    @Test fun portraitDashboardSessionAndSettingsRemainReachable() {
        val vm=ready()
        compose.onNodeWithText("이 시간을, 나답게.").assertIsDisplayed();capture("portrait-parked")
        compose.onNodeWithText("집중 시작").performScrollTo().performClick()
        compose.onNodeWithText("집중 세션").performScrollTo().assertIsDisplayed();capture("portrait-focus")
        compose.onNodeWithText("메일 확인").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("할 일 추가").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("웜 샌드").performScrollTo().performClick()
        assertEquals("sand",vm.appearance.value.preset)
        compose.onNodeWithText("야간 밝기").performScrollTo().performClick();capture("portrait-settings")
    }
    @Test @Config(qualifiers="w600dp-h800dp-port-mdpi")
    fun singleCellWindowAndOfflineMapHaveReachableControls() {
        val vm=ready();capture("single-cell-parked")
        compose.runOnIdle {vm.engine.setConnectivity(network=false)}
        compose.onNodeWithText("지도").performClick()
        compose.onNodeWithText("멈춘 시간, 어디에서 보낼까요?").assertIsDisplayed()
        compose.onNodeWithText("그린 산책로").performScrollTo().performClick()
        compose.onNodeWithText("이 장소로 계획").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("내비").performClick()
        compose.onNodeWithText("가게명·지점·도시·주소 또는 위도,경도").assertIsDisplayed()
        capture("single-cell-navigation")
    }
}
