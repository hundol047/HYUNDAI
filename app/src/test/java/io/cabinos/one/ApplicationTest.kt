package io.cabinos.one

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import android.graphics.Bitmap
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import io.cabinos.one.data.*
import io.cabinos.one.ui.CabinViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApplicationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun activityLaunchAndDrivingSafetyAreRendered() {
        compose.onNodeWithText("CabinOS").assertIsDisplayed()
        val vm = ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(10_000) { vm.state.value.sample != null }
        compose.runOnIdle { vm.selectScenario("주행") }
        compose.onNodeWithText("안전한 여정, 함께할게요.").assertIsDisplayed()
        compose.runOnIdle { vm.selectScenario("신호 대기") }
        compose.onNodeWithText("신호 대기 중").assertIsDisplayed()
        assertFalse(vm.state.value.parkedAllowed)
        compose.runOnIdle { vm.command("25분 집중 모드 켜줘") }
        assertNull(vm.state.value.session)
        compose.runOnIdle { vm.selectScenario("주차") }
        Thread.sleep(1600)
        compose.runOnIdle {
            vm.engine.acceptVehicle(VehicleSample(gear=Gear.PARK,parkingReliable=true,timestampMs=System.currentTimeMillis()))
            vm.waiting(40)
        }
        compose.onNodeWithText("StopSense  +  CabinOS").assertIsDisplayed()
        capture("parked")
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("당신에게 맞춘 CabinOS").assertIsDisplayed()
        compose.runOnIdle { vm.engine.tick() }
        compose.onNodeWithText("당신에게 맞춘 CabinOS").assertIsDisplayed()
        compose.onNodeWithText("홈").performClick()
        compose.runOnIdle { vm.start(SessionKind.FOCUS,25) }
        compose.onNodeWithText("나의 할 일").performScrollTo().assertIsDisplayed()
        capture("focus")
        compose.runOnIdle { vm.selectScenario("주행") }
        compose.onNodeWithText("안전한 여정, 함께할게요.").assertIsDisplayed()
        assertEquals(SessionStatus.PAUSED,vm.state.value.session!!.status)
        capture("driving")
    }
    private fun capture(name:String) {
        val output=File("build/screenshots/$name.png")
        output.parentFile?.mkdirs()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(image))
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            image.recycle()
        }
    }

    @Test fun realRoomDatabasePreservesCheckpointSettingsAndHistory() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),CabinDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.dao()
            val p=Preferences(focusMinutes=45,departureBufferMinutes=10,voiceEnabled=false)
            val session=CabinSession("checkpoint-session",SessionKind.FOCUS,1000,1500,900,SessionStatus.RUNNING)
            val completed=CabinSession("finished",SessionKind.RELAX,100,600,0,SessionStatus.COMPLETED)
            dao.saveState(AppState(preferences=p,session=session,history=listOf(completed),schedule=SharedSchedule(departureAtMs=500_000)))
            val checkpoint=dao.checkpoint()!!
            assertEquals("checkpoint-session",checkpoint.activeSessionId)
            assertEquals(500_000L,checkpoint.departure)
            val restored=UnifiedVehicleContextEngine({2000L})
            restored.restore(dao.preferences()!!.domain(),dao.session(checkpoint.activeSessionId!!)!!.domain(),dao.history().map{it.domain()},checkpoint.departure)
            assertEquals(45,restored.state.value.preferences.focusMinutes)
            assertEquals(SessionStatus.PAUSED,restored.state.value.session!!.status)
            assertEquals(900L,restored.state.value.session!!.remainingSeconds)
            assertEquals(1,restored.state.value.history.size)
            assertFalse(restored.state.value.parkedAllowed)
        } finally { db.close() }
    }
}
