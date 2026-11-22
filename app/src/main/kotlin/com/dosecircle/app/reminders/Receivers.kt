package com.dosecircle.app.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import com.dosecircle.core.AlertKind
import com.dosecircle.core.DoseKeys
import com.dosecircle.core.DoseStatus
import com.dosecircle.core.LogRequest
import com.dosecircle.core.RolePolicy
import java.time.Instant

/** Runs [work] on a background thread while keeping the receiver alive, then always releases it. */
private fun BroadcastReceiver.async(work: () -> Unit) {
    val pending = goAsync()
    Thread {
        try {
            work()
        } catch (t: Throwable) {
            android.util.Log.e("DoseCircle", "Receiver work failed", t)
        } finally {
            pending.finish()
        }
    }.start()
}

private fun Context.host(): ReminderHost = applicationContext as ReminderHost

/** Fires when an alarm is due. Posts the notification from the payload, then extends the schedule. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = AlertPayload.from(intent) ?: return
        val host = context.host()
        async {
            if (stillNeeded(host, payload)) {
                Channels.ensure(context)
                val settings = host.store.settings()
                val me = host.store.members().firstOrNull { it.id == settings.thisDeviceMemberId }
                val canLog = me != null && RolePolicy.canLogDoses(me.role)
                val notification = NotificationFactory.build(context, payload, canLog, settings.reminders.snoozeMinutes)
                context.getSystemService(NotificationManager::class.java).notify(payload.notificationId, notification)
            }
            host.scheduler.reschedule()
        }
    }

    /** A dose may have been logged since this alarm was set (another phone, the app, a notification button). */
    private fun stillNeeded(host: ReminderHost, p: AlertPayload): Boolean {
        if (p.kind == AlertKind.REFILL || p.medIds.isEmpty()) return true
        return p.medIds.any { medId ->
            val e = host.store.entryByKey(DoseKeys.make(medId, p.dueLocal))
            e == null || (e.status != DoseStatus.TAKEN && e.status != DoseStatus.SKIPPED)
        }
    }
}

/** Handles the Taken and Snooze buttons on notifications. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = AlertPayload.from(intent) ?: return
        val host = context.host()
        val nm = context.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            NotificationFactory.ACTION_TAKEN -> async {
                val memberId = host.store.settings().thisDeviceMemberId
                var notice: String? = null
                if (memberId != null) {
                    val settings = host.store.settings()
                    val members = host.store.members()
                    val recipient = host.store.recipients().firstOrNull { it.id == payload.recipientId }
                    for (medId in payload.medIds) {
                        val result = host.doseService.log(
                            LogRequest(medId, DoseKeys.make(medId, payload.dueLocal), payload.dueLocal, DoseStatus.TAKEN, memberId),
                        )
                        // One tap never overrides the double-dose guard. The person is asked in the app instead.
                        if (result.needsConfirmation) {
                            val med = host.store.medication(medId)
                            if (med != null) notice = GuardMessages.describe(med, result.guard, recipient, members, settings)
                        }
                    }
                }
                if (notice != null) {
                    nm.notify(payload.notificationId, NotificationFactory.buildGuardNotice(context, payload, notice))
                } else {
                    nm.cancel(payload.notificationId)
                }
                host.scheduler.reschedule()
            }
            NotificationFactory.ACTION_SNOOZE -> async {
                nm.cancel(payload.notificationId)
                host.scheduler.snooze(payload, host.store.settings().reminders.snoozeMinutes)
            }
        }
    }
}

/** Alarms do not survive a reboot or an app update, so rebuild them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (!relevant) return
        val host = context.host()
        async { host.scheduler.reschedule() }
    }
}
