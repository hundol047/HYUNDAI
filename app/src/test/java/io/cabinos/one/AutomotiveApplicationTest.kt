package io.cabinos.one

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import io.cabinos.core.*
import io.cabinos.one.ui.CabinViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Automotive feature flag only: Robolectric is NOT a Pleos Connect Emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=CabinApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomotiveApplicationTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val automotiveFeature=object:ExternalResource(){
        override fun before(){
            val context=ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context as android.app.Application).denyPermissions("android.car.permission.CAR_SPEED","android.car.permission.CAR_ENERGY")
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE,true)
            // This JVM has no vehicle services. Robolectric's default successful bind passes
            // null ComponentName/IBinder into the real SDK, which cannot represent a real bind.
            // Exercise the unavailable-service path; never substitute a successful SDK read.
            shadowOf(context as android.app.Application).setThrowInBindService(
                SecurityException("Vehicle services unavailable in JVM test"))
            shadowOf(context).setUnbindServiceCallsOnServiceDisconnected(false)
            // A previously saved phone/demo P must not override the automotive policy.
            io.cabinos.one.platform.RuntimePreferences(context).scenario="충전"
        }
    }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(automotiveFeature).around(compose)
    @Test fun automotiveStartsUnknownAndCannotSelectOrRunFakeParkScenario() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000){
            shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.state.value.sample!=null
        }
        assertTrue(vm.automotive)
        compose.runOnIdle{vm.selectScenario("충전");vm.runDemo()}
        assertEquals("실차 연결",vm.scenario.value)
        assertEquals(DataSource.VEHICLE,vm.state.value.sample?.source)
        assertFalse(vm.state.value.parkedAllowed);assertFalse(vm.demoRunning.value)
        compose.onNodeWithText("차량 상태").performClick()
        compose.onNodeWithText("차량 신호 상태").assertIsDisplayed()
        compose.onNodeWithText("전체 시나리오 실행").assertDoesNotExist()
        compose.runOnIdle{vm.setForeground(false);vm.setForeground(true)}
        assertFalse(vm.state.value.parkedAllowed)
        assertEquals(DataSource.VEHICLE,vm.state.value.sample?.source)
    }
    @Test fun missingVehiclePermissionShowsCauseAndAccessibleRequestButton() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000) { shadowOf(android.os.Looper.getMainLooper()).idle();vm.state.value.sample!=null }
        assertTrue(vm.automotive);assertFalse(vm.state.value.parkedAllowed)
        assertNotNull(shadowOf(compose.activity).lastRequestedPermission)
        compose.onNodeWithText(vm.vehicleStatus.value).assertIsDisplayed()
        compose.onNodeWithText("차량 읽기 권한 허용").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
    @Test fun vehicleDiagnosticsDisplaysSpeedSignalAge() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000) { shadowOf(android.os.Looper.getMainLooper()).idle();vm.state.value.sample!=null }
        compose.onNodeWithText("차량 상태").performClick()
        compose.onNodeWithText("속도 신호 나이",substring=true).assertIsDisplayed()
    }
    @Test fun deniedVehiclePermissionDoesNotPromptAgainOnForegroundReturn() {
        val vm=ViewModelProvider(compose.activity)[CabinViewModel::class.java]
        compose.waitUntil(20_000) { shadowOf(android.os.Looper.getMainLooper()).idle();vm.state.value.sample!=null }
        val request=shadowOf(compose.activity).lastRequestedPermission!!
        assertTrue(io.cabinos.one.platform.RuntimePreferences(compose.activity).vehiclePermissionRequested)
        val invalidations=vm.state.value.events.count { it.name=="TelemetryInvalidated" }
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(request.requestCode,request.requestedPermissions,
                IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED })
        }
        compose.runOnIdle { assertEquals(invalidations,vm.state.value.events.count { it.name=="TelemetryInvalidated" }) }
        compose.runOnIdle {
            val lifecycle=compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
        compose.runOnIdle { assertSame(request,shadowOf(compose.activity).lastRequestedPermission) }
        assertFalse(vm.state.value.parkedAllowed)
        compose.onNodeWithText("차량 읽기 권한 허용").performClick()
        compose.runOnIdle { assertNotSame(request,shadowOf(compose.activity).lastRequestedPermission) }
    }
}
