package com.dosecircle.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dosecircle.app.reminders.ReminderHealth
import com.dosecircle.app.reminders.ReminderScheduler
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.components.TonalButton

fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Some OEM builds lack a specific settings screen; fall back to the app's own settings page.
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Shows whether reminders can actually reach the person, with a one-tap fix for each problem.
 * Used in onboarding and in Settings, because a reminder app whose alarms are silently blocked is worse than none.
 */
@Composable
fun ReminderHealthCard(health: ReminderHealth, scheduler: ReminderScheduler, onChanged: () -> Unit, onSendTest: () -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied twice means Android won't ask again, so send the person to the system page instead.
        if (!granted) context.startSafely(scheduler.notificationSettingsIntent())
        onChanged()
    }

    SurfaceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HealthRow(
                "Notifications", health.notificationsAllowed,
                if (health.notificationsAllowed) "On" else "Off. You won't be reminded.",
                action = if (health.notificationsAllowed) null else "Allow",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startSafely(scheduler.notificationSettingsIntent())
            }
            HealthRow(
                "On-time reminders", health.exactAlarmsAllowed,
                if (health.exactAlarmsAllowed) "Reminders arrive at the exact minute." else "Reminders may arrive late.",
                action = if (health.exactAlarmsAllowed) null else "Fix",
            ) { context.startSafely(scheduler.exactAlarmSettingsIntent()) }
            HealthRow(
                "Battery saver", health.batteryUnrestricted,
                if (health.batteryUnrestricted) "Not restricting DoseCircle." else "Some phones delay reminders to save battery. Set DoseCircle to “Unrestricted” if you notice late reminders.",
                action = if (health.batteryUnrestricted) null else "Open",
            ) { context.startSafely(scheduler.batterySettingsIntent()) }
            TonalButton("Send a test reminder", onSendTest)
        }
    }
}

@Composable
private fun HealthRow(title: String, ok: Boolean, detail: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(detail)
        }
        if (action != null) androidx.compose.material3.TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}
