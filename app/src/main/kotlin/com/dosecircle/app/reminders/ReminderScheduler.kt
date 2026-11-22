package com.dosecircle.app.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.dosecircle.core.AlertChannel
import com.dosecircle.core.AlertDiff
import com.dosecircle.core.AlertKind
import com.dosecircle.core.DataStore
import com.dosecircle.core.DoseKeys
import com.dosecircle.core.PlannerInput
import com.dosecircle.core.ReminderPlanner
import com.dosecircle.core.ZoneTime
import java.time.Instant
import java.time.LocalDateTime

data class ReminderHealth(val notificationsAllowed: Boolean, val exactAlarmsAllowed: Boolean, val batteryUnrestricted: Boolean) {
    val allGood: Boolean get() = notificationsAllowed && exactAlarmsAllowed
}

/**
 * Turns the core ReminderPlanner's plan into AlarmManager alarms on this phone.
 * Call reschedule() (off the main thread) whenever data, settings, plan or permissions change, and on boot.
 */
class ReminderScheduler(context: Context, private val store: DataStore) {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val prefs = app.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val lock = Any()

    fun reschedule() = synchronized(lock) {
        Channels.ensure(app)
        val previous = loadIds()
        val settings = store.settings()
        val members = store.members()
        val me = members.firstOrNull { it.id == settings.thisDeviceMemberId }
        if (me == null || !settings.onboardingDone) {
            previous.forEach(::cancelAlarm); saveIds(emptySet()); return@synchronized
        }

        val now = Instant.now()
        val recipients = store.recipients()
        val entries = recipients.flatMap { r ->
            val today = ZoneTime.today(now, ZoneTime.resolve(r.timeZoneId))
            store.entries(r.id, today.minusDays(2), today.plusDays(5))
        }
        val plan = ReminderPlanner.plan(
            PlannerInput(
                now = now, settings = settings, member = me, members = members, recipients = recipients,
                medications = store.medications(null, false), entries = entries, tier = settings.effectiveTier(now),
            ),
        )
        val diff = AlertDiff.compute(previous, plan)
        diff.toCancel.forEach(::cancelAlarm)
        for (alert in diff.toSchedule) schedule(AlertPayload.of(alert), alert.trigger.toEpochMilli(), alert.id)
        saveIds(plan.map { it.id }.toSet())
    }

    /** One-off alarm outside the plan (snooze, test). The next reschedule() leaves these alone. */
    fun scheduleOneOff(payload: AlertPayload, triggerAt: Instant, requestCode: Int) =
        schedule(payload, triggerAt.toEpochMilli(), requestCode)

    fun snooze(payload: AlertPayload, minutes: Int) {
        val code = DoseKeys.stableId("snooze|${payload.slotKey}|${payload.kind}")
        scheduleOneOff(payload.copy(id = code, kind = AlertKind.REMINDER), Instant.now().plusSeconds(minutes * 60L), code)
    }

    fun sendTest(secondsFromNow: Int = 8) {
        val due = LocalDateTime.now()
        val payload = AlertPayload(
            id = TEST_ID, kind = AlertKind.REMINDER, title = "Test reminder",
            body = "If you can see this, reminders will reach you.", slotKey = "test", recipientId = "",
            dueLocal = due, medIds = emptyList(), critical = false,
        )
        scheduleOneOff(payload, Instant.now().plusSeconds(secondsFromNow.toLong()), TEST_ID)
    }

    /** Clears delivered notifications for a slot once its doses are handled. */
    fun dismissSlot(slotKey: String) {
        for (channel in AlertChannel.values()) {
            notifications.cancel(DoseKeys.stableId("notif|$channel|$slotKey"))
        }
    }

    fun cancelEverything() = synchronized(lock) {
        loadIds().forEach(::cancelAlarm); saveIds(emptySet()); notifications.cancelAll()
    }

    fun health(): ReminderHealth {
        val power = app.getSystemService(PowerManager::class.java)
        return ReminderHealth(
            notificationsAllowed = notifications.areNotificationsEnabled(),
            exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms(),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(app.packageName),
        )
    }

    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${app.packageName}"))
        } else {
            notificationSettingsIntent()
        }

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)

    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    // ----- internals -----

    private fun schedule(payload: AlertPayload, triggerAtMillis: Long, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            app, requestCode, payload.toIntent(app, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactOk) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call: fall back to a window the system may batch.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        // Matching only needs the same component and request code, not the extras.
        val pi = PendingIntent.getBroadcast(
            app, requestCode, Intent(app, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarms.cancel(pi)
        pi.cancel()
    }

    private fun loadIds(): Set<Int> = prefs.getStringSet(KEY, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
    private fun saveIds(ids: Set<Int>) = prefs.edit().putStringSet(KEY, ids.map { it.toString() }.toSet()).apply()

    private companion object {
        const val KEY = "planned_ids"
        const val TEST_ID = 7_000_001
    }
}
