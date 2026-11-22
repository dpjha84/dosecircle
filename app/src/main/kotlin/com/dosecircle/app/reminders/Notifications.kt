package com.dosecircle.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.dosecircle.app.MainActivity
import com.dosecircle.app.R
import com.dosecircle.core.AlertChannel
import com.dosecircle.core.AlertKind

object Channels {
    const val DOSE = "dose_reminders"
    const val CAREGIVER = "caregiver_alerts"
    const val REFILL = "refills"

    fun idFor(channel: AlertChannel) = when (channel) {
        AlertChannel.DOSE_REMINDERS -> DOSE
        AlertChannel.CAREGIVER_ALERTS -> CAREGIVER
        AlertChannel.REFILLS -> REFILL
    }

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun make(id: String, name: String, description: String, importance: Int) {
            if (nm.getNotificationChannel(id) != null) return
            val channel = NotificationChannel(id, name, importance).apply {
                this.description = description
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            nm.createNotificationChannel(channel)
        }
        make(DOSE, "Dose reminders", "Reminders when it is time to take medicines.", NotificationManager.IMPORTANCE_HIGH)
        make(CAREGIVER, "Missed-dose alerts", "Alerts when someone you care for has not had a dose.", NotificationManager.IMPORTANCE_HIGH)
        make(REFILL, "Refill reminders", "A heads-up before a medicine runs out.", NotificationManager.IMPORTANCE_DEFAULT)
    }
}

object NotificationFactory {
    const val ACTION_TAKEN = "com.dosecircle.app.action.TAKEN"
    const val ACTION_SNOOZE = "com.dosecircle.app.action.SNOOZE"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun build(context: Context, p: AlertPayload, canLog: Boolean, snoozeMinutes: Int): Notification {
        val open = PendingIntent.getActivity(
            context, p.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            FLAGS,
        )
        // Medicine names are private: the lock screen only shows a generic line.
        val public = Notification.Builder(context, Channels.idFor(p.channel))
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle("DoseCircle")
            .setContentText("A reminder is waiting")
            .build()

        val builder = Notification.Builder(context, Channels.idFor(p.channel))
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(p.title)
            .setContentText(p.body)
            .setStyle(Notification.BigTextStyle().bigText(p.body))
            .setCategory(if (p.kind == AlertKind.MISSED) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)

        val actionable = p.kind != AlertKind.REFILL && p.medIds.isNotEmpty() && canLog
        if (actionable) {
            builder.addAction(action(context, p, ACTION_TAKEN, if (p.kind == AlertKind.MISSED) "Mark as taken" else "Taken"))
            if (p.kind != AlertKind.MISSED) builder.addAction(action(context, p, ACTION_SNOOZE, "Snooze $snoozeMinutes min"))
        }
        return builder.build()
    }

    /** Shown instead of logging when the double-dose guard stops a one-tap action. */
    fun buildGuardNotice(context: Context, p: AlertPayload, message: String): Notification {
        val open = PendingIntent.getActivity(
            context, p.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            FLAGS,
        )
        return Notification.Builder(context, Channels.DOSE)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("Check before logging")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText("$message Open DoseCircle to review."))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private fun action(context: Context, p: AlertPayload, action: String, label: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(context, p.notificationId, p.toIntent(context, ActionReceiver::class.java, action), FLAGS)
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_dose), label, pi).build()
    }
}
