package com.dosecircle.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

enum class HistoryOutcome { TAKEN, TAKEN_LATE, SKIPPED, MISSED, AS_NEEDED }

data class HistoryRow(
    val dueLocal: LocalDateTime,
    val medicationId: String,
    val medicationName: String,
    val strength: String = "",
    val outcome: HistoryOutcome,
    val loggedAtLocal: LocalDateTime? = null,
    val loggedBy: String = "",
    val note: String = "",
) {
    val date: LocalDate get() = dueLocal.toLocalDate()
    val outcomeText: String
        get() = when (outcome) {
            HistoryOutcome.TAKEN -> "Taken"
            HistoryOutcome.TAKEN_LATE -> "Taken late"
            HistoryOutcome.SKIPPED -> "Skipped"
            HistoryOutcome.MISSED -> "Missed"
            HistoryOutcome.AS_NEEDED -> "Taken (as needed)"
        }
}

object HistoryBuilder {
    const val LATE_AFTER_MINUTES = 60L

    /** Every decided dose in the range: logged ones, plus scheduled ones that passed unlogged (missed). */
    fun build(
        recipient: CareRecipient,
        medications: List<Medication>,
        entries: List<DoseEntry>,
        members: List<CircleMember>,
        from: LocalDate,
        to: LocalDate,
        now: Instant,
        settings: ReminderSettings,
    ): List<HistoryRow> {
        val zone = ZoneTime.resolve(recipient.timeZoneId)
        val meds = medications.filter { it.recipientId == recipient.id }.associateBy { it.id }
        val who = members.associate { it.id to it.name }
        val inRange = entries.filter { it.recipientId == recipient.id && it.dateKey >= from.toString() && it.dateKey <= to.toString() }
        val byKey = HashMap<String, DoseEntry>()
        inRange.filter { !it.isAsNeeded }.sortedBy { it.loggedAt }.forEach { byKey[it.occurrenceKey] = it }

        val rows = ArrayList<HistoryRow>()
        val used = HashSet<String>()
        for (med in meds.values) {
            for (due in ScheduleExpander.expand(med, from, to)) {
                val key = DoseKeys.make(med.id, due)
                val dueUtc = ZoneTime.toInstant(due, zone)
                val e = byKey[key]
                if (e != null) {
                    used.add(key)
                    rows.add(rowFor(med, e, due, dueUtc, zone, who))
                } else if (!now.isBefore(dueUtc.plusSeconds(settings.missedAfterMinutes * 60L))) {
                    rows.add(HistoryRow(due, med.id, med.name, med.strength, HistoryOutcome.MISSED))
                }
            }
        }
        // Entries that no longer match the schedule (edited times) and as-needed doses.
        for (e in inRange.filter { it.isAsNeeded || it.occurrenceKey !in used }) {
            val med = meds[e.medicationId] ?: continue
            rows.add(rowFor(med, e, e.dueLocal, ZoneTime.toInstant(e.dueLocal, zone), zone, who))
        }
        return rows.sortedWith(compareBy({ it.dueLocal }, { it.medicationName.lowercase() }))
    }

    private fun rowFor(
        med: Medication, e: DoseEntry, due: LocalDateTime, dueUtc: Instant, zone: java.time.ZoneId, who: Map<String, String>,
    ): HistoryRow {
        val outcome = when {
            e.isAsNeeded -> HistoryOutcome.AS_NEEDED
            e.status == DoseStatus.SKIPPED -> HistoryOutcome.SKIPPED
            e.loggedAt.isAfter(dueUtc.plusSeconds(LATE_AFTER_MINUTES * 60)) -> HistoryOutcome.TAKEN_LATE
            else -> HistoryOutcome.TAKEN
        }
        return HistoryRow(
            due, med.id, med.name, med.strength, outcome,
            ZoneTime.toLocal(e.loggedAt, zone), who[e.loggedByMemberId] ?: "", e.note ?: "",
        )
    }
}

data class DayAdherence(val date: LocalDate, val taken: Int, val skipped: Int, val missed: Int) {
    val total: Int get() = taken + skipped + missed
}

data class MedAdherence(val medicationId: String, val name: String, val scheduled: Int, val taken: Int) {
    val percent: Double get() = if (scheduled == 0) 0.0 else Math.round(1000.0 * taken / scheduled) / 10.0
}

data class AdherenceReport(
    val from: LocalDate,
    val to: LocalDate,
    val scheduled: Int,
    val taken: Int,
    val takenLate: Int,
    val skipped: Int,
    val missed: Int,
    val days: List<DayAdherence>,
    val byMedication: List<MedAdherence>,
) {
    val percent: Double get() = if (scheduled == 0) 0.0 else Math.round(1000.0 * taken / scheduled) / 10.0
}

object AdherenceCalculator {
    /** Taken (on time or late) over every decided scheduled dose. Skipped and missed count against it. As-needed is excluded. */
    fun compute(rows: List<HistoryRow>, from: LocalDate, to: LocalDate): AdherenceReport {
        val scheduled = rows.filter { it.outcome != HistoryOutcome.AS_NEEDED }
        fun isTaken(r: HistoryRow) = r.outcome == HistoryOutcome.TAKEN || r.outcome == HistoryOutcome.TAKEN_LATE

        val days = ArrayList<DayAdherence>()
        var d = from
        while (!d.isAfter(to)) {
            val day = scheduled.filter { it.date == d }
            days.add(DayAdherence(d, day.count(::isTaken), day.count { it.outcome == HistoryOutcome.SKIPPED }, day.count { it.outcome == HistoryOutcome.MISSED }))
            d = d.plusDays(1)
        }
        val byMed = scheduled.groupBy { it.medicationId }.map { (id, g) ->
            MedAdherence(id, g.first().medicationName, g.size, g.count(::isTaken))
        }.sortedBy { it.name.lowercase() }

        return AdherenceReport(
            from, to, scheduled.size, scheduled.count(::isTaken),
            scheduled.count { it.outcome == HistoryOutcome.TAKEN_LATE },
            scheduled.count { it.outcome == HistoryOutcome.SKIPPED },
            scheduled.count { it.outcome == HistoryOutcome.MISSED },
            days, byMed,
        )
    }
}

object CsvExporter {
    fun build(personName: String, rows: List<HistoryRow>): String {
        val sb = StringBuilder("Date,Due time,Person,Medicine,Strength,Result,Logged at,Logged by,Note\r\n")
        for (r in rows) {
            sb.append(
                listOf(
                    r.dueLocal.toLocalDate().toString(),
                    "%02d:%02d".format(Locale.ROOT, r.dueLocal.hour, r.dueLocal.minute),
                    personName, r.medicationName, r.strength, r.outcomeText,
                    r.loggedAtLocal?.let { "${it.toLocalDate()} ${"%02d:%02d".format(Locale.ROOT, it.hour, it.minute)}" } ?: "",
                    r.loggedBy, r.note,
                ).joinToString(",") { cell(it) },
            ).append("\r\n")
        }
        return sb.toString()
    }

    /** Quotes when needed and defuses spreadsheet formulas (=, +, -, @). */
    fun cell(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}

data class ReportMedLine(
    val name: String, val strength: String, val dose: String, val schedule: String, val instructions: String, val percent: Double?,
)

data class DoctorReport(
    val personName: String,
    val from: LocalDate,
    val to: LocalDate,
    val adherence: AdherenceReport,
    val medications: List<ReportMedLine>,
    val notes: List<HistoryRow>,
    val recentMissed: List<HistoryRow>,
) {
    companion object {
        fun build(
            recipient: CareRecipient, meds: List<Medication>, rows: List<HistoryRow>, from: LocalDate, to: LocalDate, use24h: Boolean,
        ): DoctorReport {
            val adherence = AdherenceCalculator.compute(rows, from, to)
            val lines = meds.filter { !it.isArchived }.sortedBy { it.name.lowercase() }.map { m ->
                ReportMedLine(
                    m.name, m.strength, m.doseText, ScheduleExpander.describe(m, use24h), m.instructions,
                    adherence.byMedication.firstOrNull { it.medicationId == m.id }?.percent,
                )
            }
            return DoctorReport(
                recipient.name, from, to, adherence, lines,
                rows.filter { it.note.isNotEmpty() }.sortedByDescending { it.dueLocal }.take(20),
                rows.filter { it.outcome == HistoryOutcome.MISSED }.sortedByDescending { it.dueLocal }.take(20),
            )
        }
    }
}
