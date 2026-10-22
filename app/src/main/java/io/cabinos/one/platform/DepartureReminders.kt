package io.cabinos.one.platform

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.cabinos.core.AppState
import io.cabinos.one.MainActivity
import io.cabinos.one.R
import java.util.UUID

/** Inexact OS reminders for a saved plan; never proof of parking or a background vehicle monitor. */
class DepartureReminders(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val preferences = context.getSharedPreferences("departure-reminder", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    init {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "복귀·출발 준비 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "저장한 출발 계획을 확인하도록 안내합니다. 알림은 기기 절전 상태에 따라 늦어질 수 있습니다."
        })
    }
    fun permitted(): Boolean = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun sync(state: AppState) {
        if (state.echoRoadActive) { cancel(); return }
        // Losing foreground/telemetry does not fabricate a new parking state or erase an already saved reminder.
        if (!state.parkedAllowed) return
        val departure = state.schedule.departureAtMs ?: run { cancel(); return }
        // Place plans reserve a full round trip. Once outside, allow the return half before preparation.
        val returnWalk = if(state.selectedActivityId!=null)state.schedule.returnSeconds/2 else 0
        val alert = departure - (state.schedule.bufferSeconds + returnWalk) * 1000
        if (alert <= clock()) {
            // A shortened plan must invalidate the previous future alarm as well.
            if (preferences.contains("token")) cancel()
            return // The foreground engine handles reminders already due.
        }
        if (preferences.getLong("departure", 0) == departure && preferences.getLong("alert", 0) == alert) return
        val token = UUID.randomUUID().toString()
        preferences.edit().putLong("departure", departure).putLong("alert", alert).putString("token", token).commit()
        notifications.cancel(NOTIFICATION_ID)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert, pending(token))
    }
    private fun pending(token: String? = null): PendingIntent = PendingIntent.getBroadcast(context, REQUEST_ID,
        Intent(context, DepartureReminderReceiver::class.java).putExtra("token", token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancel() {
        alarms.cancel(pending())
        preferences.edit().clear().commit()
        notifications.cancel(NOTIFICATION_ID)
    }
    /** Consume exactly one current reminder. Stale/replayed alarms cannot notify or run parked commands. */
    fun deliver(token: String?): Boolean {
        if (token == null || token != preferences.getString("token", null)) return false
        val alert = preferences.getLong("alert", 0)
        val departure = preferences.getLong("departure", 0)
        val now = clock()
        if (now < alert) return false
        preferences.edit().clear().commit()
        if (now - alert > 15 * 60_000 || now - departure > 5 * 60_000 || !permitted()) return false
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("저장한 출발 계획을 확인하세요")
            .setContentText("계획한 복귀·출발 준비 시간입니다. 현재 차량 상황과 일정을 확인해 주세요.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
        return try { notifications.notify(NOTIFICATION_ID, notification); true } catch (_: SecurityException) { false }
    }
    companion object {
        const val CHANNEL = "departure-plans"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_ID = 1001
    }
}
class DepartureReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DepartureReminders(context).deliver(intent.getStringExtra("token"))
    }
}
