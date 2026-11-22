package com.dosecircle.app.data

import android.content.Context
import androidx.room.Room
import com.dosecircle.core.AppSettings
import com.dosecircle.core.CareRecipient
import com.dosecircle.core.CircleMember
import com.dosecircle.core.CircleRole
import com.dosecircle.core.DataStore
import com.dosecircle.core.DoseEntry
import com.dosecircle.core.DosePriority
import com.dosecircle.core.DoseStatus
import com.dosecircle.core.EscalationPolicy
import com.dosecircle.core.MedForm
import com.dosecircle.core.Medication
import com.dosecircle.core.PlanTier
import com.dosecircle.core.ReminderSettings
import com.dosecircle.core.ReminderStyle
import com.dosecircle.core.ScheduleKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Room-backed implementation of the core DataStore port. All calls are blocking: never call them on the main thread.
 * Settings live in SharedPreferences because they are tiny and receivers need them synchronously.
 */
class RoomDataStore(context: Context) : DataStore {
    private val appContext = context.applicationContext
    private val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "dosecircle.db").build()
    private val dao = db.dao()
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ----- recipients & members -----
    override fun recipients(): List<CareRecipient> = dao.recipients().map { it.toModel() }
    override fun saveRecipient(r: CareRecipient) = dao.upsert(r.toEntity())
    override fun deleteRecipient(id: String) {
        db.runInTransaction {
            dao.deleteEntriesOf(id); dao.deleteMedicationsOf(id); dao.deleteRecipient(id)
        }
    }

    override fun members(): List<CircleMember> = dao.members().map { it.toModel() }
    override fun saveMember(m: CircleMember) = dao.upsert(m.toEntity())
    override fun deleteMember(id: String) = dao.deleteMember(id)

    // ----- medications -----
    override fun medications(recipientId: String?, includeArchived: Boolean): List<Medication> =
        dao.medications(recipientId, includeArchived).map { it.toModel() }

    override fun medication(id: String): Medication? = dao.medication(id)?.toModel()
    override fun saveMedication(m: Medication) = dao.upsert(m.toEntity())
    override fun deleteMedication(id: String) {
        db.runInTransaction { dao.deleteEntriesOfMedication(id); dao.deleteMedication(id) }
    }

    // ----- entries -----
    override fun entries(recipientId: String, from: LocalDate, to: LocalDate): List<DoseEntry> =
        dao.entries(recipientId, from.toString(), to.toString()).map { it.toModel() }

    override fun entry(id: String): DoseEntry? = dao.entry(id)?.toModel()
    override fun entryByKey(occurrenceKey: String): DoseEntry? = dao.entryByKey(occurrenceKey)?.toModel()
    override fun recentEntriesForMed(medicationId: String, since: Instant): List<DoseEntry> =
        dao.recentEntries(medicationId, since.toEpochMilli()).map { it.toModel() }

    override fun saveEntry(e: DoseEntry) = dao.upsert(e.toEntity())
    override fun deleteEntry(id: String) = dao.deleteEntry(id)

    // ----- settings -----
    override fun settings(): AppSettings {
        val r = ReminderSettings()
        return AppSettings(
            reminders = ReminderSettings(
                style = enumOr(prefs.getString("style", null), r.style),
                headsUpMinutes = prefs.getInt("headsUp", r.headsUpMinutes),
                snoozeMinutes = prefs.getInt("snooze", r.snoozeMinutes),
                nagIntervalMinutes = prefs.getInt("nagEvery", r.nagIntervalMinutes),
                nagCount = prefs.getInt("nagCount", r.nagCount),
                missedAfterMinutes = prefs.getInt("missedAfter", r.missedAfterMinutes),
                quietHoursEnabled = prefs.getBoolean("quiet", r.quietHoursEnabled),
                quietStartMinutes = prefs.getInt("quietStart", r.quietStartMinutes),
                quietEndMinutes = prefs.getInt("quietEnd", r.quietEndMinutes),
            ),
            tier = enumOr(prefs.getString("tier", null), PlanTier.FREE),
            proExpires = prefs.getLong("proExpires", -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
            thisDeviceMemberId = prefs.getString("me", null),
            activeRecipientId = prefs.getString("active", null),
            lastLoggerMemberId = prefs.getString("lastLogger", null),
            onboardingDone = prefs.getBoolean("onboarded", false),
            use24Hour = prefs.getBoolean("use24h", false),
            comfortMode = prefs.getBoolean("comfort", false),
        )
    }

    override fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("style", s.reminders.style.name)
            .putInt("headsUp", s.reminders.headsUpMinutes)
            .putInt("snooze", s.reminders.snoozeMinutes)
            .putInt("nagEvery", s.reminders.nagIntervalMinutes)
            .putInt("nagCount", s.reminders.nagCount)
            .putInt("missedAfter", s.reminders.missedAfterMinutes)
            .putBoolean("quiet", s.reminders.quietHoursEnabled)
            .putInt("quietStart", s.reminders.quietStartMinutes)
            .putInt("quietEnd", s.reminders.quietEndMinutes)
            .putString("tier", s.tier.name)
            .putLong("proExpires", s.proExpires?.toEpochMilli() ?: -1L)
            .putString("me", s.thisDeviceMemberId)
            .putString("active", s.activeRecipientId)
            .putString("lastLogger", s.lastLoggerMemberId)
            .putBoolean("onboarded", s.onboardingDone)
            .putBoolean("use24h", s.use24Hour)
            .putBoolean("comfort", s.comfortMode)
            .apply()
    }

    override fun eraseEverything() {
        db.clearAllTables()
        prefs.edit().clear().apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}

// ----- mappers -----

private fun csv(values: List<String>) = values.joinToString(",")
private fun splitCsv(text: String) = if (text.isBlank()) emptyList() else text.split(",")

private inline fun <reified E : Enum<E>> parse(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun RecipientEntity.toModel() = CareRecipient(
    id = id, name = name, timeZoneId = timeZoneId,
    escalation = EscalationPolicy(firstAlertAfterMinutes, stepGapMinutes, criticalSpeedFactor, splitCsv(chain)),
)

private fun CareRecipient.toEntity() = RecipientEntity(
    id, name, timeZoneId, escalation.firstAlertAfterMinutes, escalation.stepGapMinutes, escalation.criticalSpeedFactor,
    csv(escalation.chainMemberIds),
)

private fun MemberEntity.toModel() = CircleMember(
    id, name, parse(role, CircleRole.CAREGIVER), timeZoneId, isThisDevice, receivesDoseReminders, receivesMissedAlerts,
)

private fun CircleMember.toEntity() = MemberEntity(id, name, role.name, timeZoneId, isThisDevice, receivesDoseReminders, receivesMissedAlerts)

private fun MedicationEntity.toModel() = Medication(
    id = id, recipientId = recipientId, name = name, strength = strength, form = parse(form, MedForm.TABLET),
    doseText = doseText, instructions = instructions, kind = parse(kind, ScheduleKind.DAILY),
    timesOfDayMinutes = splitCsv(times).mapNotNull { it.toIntOrNull() }, daysOfWeekMask = daysOfWeekMask,
    intervalDays = intervalDays, startDate = LocalDate.parse(startDate), endDate = endDate?.let(LocalDate::parse),
    priority = parse(priority, DosePriority.NORMAL), minIntervalMinutes = minIntervalMinutes, unitsPerDose = unitsPerDose,
    unitsRemaining = unitsRemaining, refillThresholdDays = refillThresholdDays, colorIndex = colorIndex, isArchived = isArchived,
)

private fun Medication.toEntity() = MedicationEntity(
    id, recipientId, name, strength, form.name, doseText, instructions, kind.name,
    csv(timesOfDayMinutes.map { it.toString() }), daysOfWeekMask, intervalDays, startDate.toString(), endDate?.toString(),
    priority.name, minIntervalMinutes, unitsPerDose, unitsRemaining, refillThresholdDays, colorIndex, isArchived,
)

private fun EntryEntity.toModel() = DoseEntry(
    id = id, recipientId = recipientId, medicationId = medicationId, occurrenceKey = occurrenceKey,
    dueLocal = LocalDateTime.parse(dueLocal), status = parse(status, DoseStatus.TAKEN),
    loggedAt = Instant.ofEpochMilli(loggedAtMillis), loggedByMemberId = loggedBy, note = note, isAsNeeded = isAsNeeded,
)

private fun DoseEntry.toEntity() = EntryEntity(
    id, recipientId, medicationId, occurrenceKey, dueLocal.toString(), dateKey, status.name,
    loggedAt.toEpochMilli(), loggedByMemberId, note, isAsNeeded,
)
