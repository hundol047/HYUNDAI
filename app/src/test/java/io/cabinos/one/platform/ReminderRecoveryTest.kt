package io.cabinos.one.platform

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ReminderRecoveryTest {
    @Test fun bootRecreatesSavedAlarmExactlyOnceWithoutOpeningParkedFeatures() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val now=System.currentTimeMillis()
        val reminders=DepartureReminders(c)
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(now+3_600_000,now,300)))
        val alarms=c.getSystemService(AlarmManager::class.java)
        val before=shadowOf(alarms).scheduledAlarms.single()
        alarms.cancel(before.operation as android.app.PendingIntent)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        val receiver=DepartureRestoreReceiver()
        repeat(2){receiver.onReceive(c,Intent(Intent.ACTION_BOOT_COMPLETED))}
        assertEquals(1,shadowOf(alarms).scheduledAlarms.size)
        assertEquals(before.triggerAtTime,shadowOf(alarms).scheduledAlarms.single().triggerAtTime)
        assertFalse(UnifiedVehicleContextEngine().state.value.parkedAllowed)
    }
    @Test fun expiredAlarmIsDiscardedAndCannotBeReplayed() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        var now=1_000_000L
        val reminders=DepartureReminders(c){now}
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(now+3_600_000,now,300)))
        val prefs=c.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE)
        val token=prefs.getString("token",null)
        now+=7_200_000
        reminders.restoreSaved()
        assertFalse(prefs.contains("token"))
        assertFalse(reminders.deliver(token))
        assertTrue(shadowOf(c.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
    @Test fun deletedPlanIsNotRestoredOnBootOrPackageUpdate() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val now=System.currentTimeMillis()
        val reminders=DepartureReminders(c)
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(now+3_600_000,now,300)))
        reminders.cancel()
        val receiver=DepartureRestoreReceiver()
        receiver.onReceive(c,Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(c,Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(shadowOf(c.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }
}
