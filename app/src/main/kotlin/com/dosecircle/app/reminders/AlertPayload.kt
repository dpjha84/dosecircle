package com.dosecircle.app.reminders

import android.content.Context
import android.content.Intent
import com.dosecircle.core.AlertChannel
import com.dosecircle.core.AlertKind
import com.dosecircle.core.PlannedAlert
import java.time.LocalDateTime

/** Everything an alarm needs to post its notification, carried in the PendingIntent so no database read is required to show it. */
data class AlertPayload(
    val id: Int,
    val kind: AlertKind,
    val title: String,
    val body: String,
    val slotKey: String,
    val recipientId: String,
    val dueLocal: LocalDateTime,
    val medIds: List<String>,
    val critical: Boolean,
) {
    val channel: AlertChannel
        get() = when (kind) {
            AlertKind.MISSED -> AlertChannel.CAREGIVER_ALERTS
            AlertKind.REFILL -> AlertChannel.REFILLS
            else -> AlertChannel.DOSE_REMINDERS
        }

    /** One notification per slot and channel, so repeated nags replace each other instead of piling up. */
    val notificationId: Int get() = com.dosecircle.core.DoseKeys.stableId("notif|$channel|$slotKey")

    fun toIntent(context: Context, target: Class<*>, action: String? = null): Intent =
        Intent(context, target).apply {
            if (action != null) this.action = action
            putExtra(ID, id)
            putExtra(KIND, kind.name)
            putExtra(TITLE, title)
            putExtra(BODY, body)
            putExtra(SLOT, slotKey)
            putExtra(RECIPIENT, recipientId)
            putExtra(DUE, dueLocal.toString())
            putExtra(MEDS, medIds.joinToString(","))
            putExtra(CRITICAL, critical)
        }

    companion object {
        private const val ID = "id"
        private const val KIND = "kind"
        private const val TITLE = "title"
        private const val BODY = "body"
        private const val SLOT = "slot"
        private const val RECIPIENT = "recipient"
        private const val DUE = "due"
        private const val MEDS = "meds"
        private const val CRITICAL = "critical"

        fun from(intent: Intent): AlertPayload? {
            return try {
                AlertPayload(
                    id = intent.getIntExtra(ID, 0),
                    kind = AlertKind.valueOf(intent.getStringExtra(KIND) ?: return null),
                    title = intent.getStringExtra(TITLE) ?: return null,
                    body = intent.getStringExtra(BODY) ?: "",
                    slotKey = intent.getStringExtra(SLOT) ?: return null,
                    recipientId = intent.getStringExtra(RECIPIENT) ?: "",
                    dueLocal = LocalDateTime.parse(intent.getStringExtra(DUE) ?: return null),
                    medIds = (intent.getStringExtra(MEDS) ?: "").split(",").filter { it.isNotBlank() },
                    critical = intent.getBooleanExtra(CRITICAL, false),
                )
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: java.time.DateTimeException) {
                null
            }
        }

        fun of(a: PlannedAlert) = AlertPayload(
            a.id, a.kind, a.title, a.body, a.slotKey, a.recipientId, a.dueLocal, a.medicationIds, a.critical,
        )
    }
}
