package com.dosecircle.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipients")
data class RecipientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val timeZoneId: String,
    val firstAlertAfterMinutes: Int,
    val stepGapMinutes: Int,
    val criticalSpeedFactor: Double,
    /** Member ids in alert order, comma separated. */
    val chain: String,
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val timeZoneId: String,
    val isThisDevice: Boolean,
    val receivesDoseReminders: Boolean,
    val receivesMissedAlerts: Boolean,
)

@Entity(tableName = "medications", indices = [Index("recipientId")])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    val name: String,
    val strength: String,
    val form: String,
    val doseText: String,
    val instructions: String,
    val kind: String,
    /** Minutes after midnight, comma separated. */
    val times: String,
    val daysOfWeekMask: Int,
    val intervalDays: Int,
    val startDate: String,
    val endDate: String?,
    val priority: String,
    val minIntervalMinutes: Int,
    val unitsPerDose: Double,
    val unitsRemaining: Double?,
    val refillThresholdDays: Int,
    val colorIndex: Int,
    val isArchived: Boolean,
)

@Entity(
    tableName = "entries",
    indices = [
        Index(value = ["recipientId", "dateKey"]),
        Index(value = ["occurrenceKey"], unique = true),
        Index(value = ["medicationId", "loggedAtMillis"]),
    ],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val recipientId: String,
    val medicationId: String,
    val occurrenceKey: String,
    /** ISO local date-time, e.g. 2026-11-21T08:00. */
    val dueLocal: String,
    /** ISO local date, e.g. 2026-11-21. Sortable as text. */
    val dateKey: String,
    val status: String,
    val loggedAtMillis: Long,
    val loggedBy: String,
    val note: String?,
    val isAsNeeded: Boolean,
)
