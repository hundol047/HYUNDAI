package io.cabinos.one.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.cabinos.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAlarmManager

@Implements(AlarmManager::class)
class CountingAlarmManager : ShadowAlarmManager() {
    var cancellations = 0
    @Implementation override fun cancel(operation: PendingIntent) {
        cancellations++
        super.cancel(operation)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29], shadows=[CountingAlarmManager::class])
class ReminderCancellationTest {
    @Test fun newPlanDeliveredByReceiverCanStillBeClearedWhenDriving() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        var now=1_000_000L
        val reminders=DepartureReminders(context) {now}
        reminders.cancel()
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(3_000_000,now,300)))
        val token=context.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null)
        now=2_700_000
        assertTrue(DepartureReminders(context) {now}.deliver(token))
        val notifications=org.robolectric.Shadows.shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
        assertEquals(1,notifications.allNotifications.size)
        reminders.sync(AppState(echoRoadActive=true))
        assertTrue(notifications.allNotifications.isEmpty())
    }
    @Test fun repeatedDrivingUpdatesCancelTheSystemAlarmOnceButANewPlanCanBeCancelled() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val alarms=Shadow.extract<CountingAlarmManager>(context.getSystemService(AlarmManager::class.java))
        val reminders=DepartureReminders(context) {1_000_000L}
        repeat(30) {reminders.sync(AppState(echoRoadActive=true))}
        assertEquals(1,alarms.cancellations)
        reminders.sync(AppState(parkedAllowed=true,schedule=TimeOptimizer.calculate(3_000_000,1_000_000,300)))
        // AlarmManager scheduling can internally cancel a matching PendingIntent.
        val beforeDriving=alarms.cancellations
        repeat(30) {reminders.sync(AppState(echoRoadActive=true))}
        assertEquals(beforeDriving+1,alarms.cancellations)
        assertNull(context.getSharedPreferences("departure-reminder",Context.MODE_PRIVATE).getString("token",null))
    }
}
