package io.cabinos.one.platform

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PlatformTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()
    @Test @Config(sdk=[33]) fun deniedNotificationsDoNotClaimAnAlertWasPosted() {
        val c=context()
        shadowOf(c as android.app.Application).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        var now=1_000_000L
        val reminders=DepartureReminders(c) {now}
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(3_000_000,now,300)))
        val token=c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null)
        now=2_700_000
        assertFalse(reminders.permitted());assertFalse(reminders.deliver(token))
        assertTrue(shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
    @Test fun realSourceSelectionSurvivesRecreationWithoutFabricatedParking() {
        val prefs=RuntimePreferences(context())
        SimulationVehicleProvider(prefs).select("실차 연결")
        val restored=SimulationVehicleProvider(RuntimePreferences(context()))
        assertTrue(restored.realMode)
        assertEquals(DataSource.VEHICLE,restored.samples.value.source)
        assertFalse(restored.samples.value.connected)
        assertFalse(UnifiedVehicleContextEngine().state.value.parkedAllowed)
    }
    @Test fun parkedSimulationAlwaysRequiresFreshEngineConfirmationAfterRestore() {
        val prefs=RuntimePreferences(context())
        SimulationVehicleProvider(prefs).select("주차")
        val restored=SimulationVehicleProvider(RuntimePreferences(context()))
        val engine=UnifiedVehicleContextEngine()
        engine.acceptVehicle(restored.samples.value)
        assertFalse(engine.state.value.parkedAllowed)
    }
    @Test fun departureReminderIsScheduledAtSharedPreparationTimeAndConsumedOnce() {
        val now=1_000_000L
        var time=now
        val c=context();val reminders=DepartureReminders(c) { time }
        reminders.cancel()
        val departure=now+2_400_000
        val state=AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(departure,now,300))
        reminders.sync(state)
        assertEquals(departure-300_000,shadowOf(c.getSystemService(AlarmManager::class.java)).nextScheduledAlarm!!.triggerAtTime)
        val token=c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null)
        assertFalse(reminders.deliver("wrong-token"))
        assertFalse(reminders.deliver(token)) // cannot fire early
        time=departure-300_000
        assertTrue(reminders.deliver(token))
        assertFalse(reminders.deliver(token))
        assertEquals(1,shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }
    @Test fun shorteningPlanIntoPreparationCancelsTheOldFutureAlarm() {
        val c=context();val reminders=DepartureReminders(c) {1_000_000L}
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(3_000_000,1_000_000,300)))
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(1_300_000,1_000_000,300)))
        assertNull(c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null))
        assertNull(shadowOf(c.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }
    @Test fun selectedPlaceReminderAllowsWalkingBackBeforePreparation() {
        val c=context();val reminders=DepartureReminders(c) {1_000_000L}
        val departure=3_000_000L
        reminders.sync(AppState(parkedAllowed=true,selectedActivityId="cafe",schedule=TimeOptimizer.calculate(departure,1_000_000,300,360)))
        assertEquals(departure-480_000,shadowOf(c.getSystemService(AlarmManager::class.java)).nextScheduledAlarm!!.triggerAtTime)
    }
    @Test fun drivingCancelsPreviouslyArmedReminder() {
        val c=context();val reminders=DepartureReminders(c) {1_000_000L}
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(3_000_000,1_000_000,300)))
        reminders.sync(AppState(echoRoadActive=true))
        assertNull(c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null))
        assertNull(shadowOf(c.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }
    @Test fun changedDepartureRejectsOldAlarmToken() {
        val c=context();var now=1_000_000L;val reminders=DepartureReminders(c) {now}
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(3_000_000,now,300)))
        val old=c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null)
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(4_000_000,now,300)))
        now=3_700_000
        assertFalse(reminders.deliver(old))
        assertTrue(reminders.deliver(c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null)))
    }
}
