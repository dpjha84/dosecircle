package com.dosecircle.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.SortedMap

enum class SlotState { UPCOMING, DUE, MISSED, DONE }

data class DoseItem(
    val medication: Medication,
    val key: String,
    val dueLocal: LocalDateTime,
    val status: DoseStatus,
    val entry: DoseEntry?,
) {
    val isPending: Boolean get() = status == DoseStatus.UPCOMING || status == DoseStatus.DUE || status == DoseStatus.MISSED
}

data class DoseSlot(
    val recipientId: String,
    val dueLocal: LocalDateTime,
    val dueUtc: Instant,
    val items: List<DoseItem>,
    val state: SlotState,
) {
    val pendingCount: Int get() = items.count { it.isPending }
    val hasCritical: Boolean get() = items.any { it.isPending && it.medication.priority == DosePriority.CRITICAL }
    val slotKey: String get() = DoseKeys.slot(recipientId, dueLocal)
}

data class DaySummary(val total: Int, val taken: Int, val skipped: Int, val pending: Int, val missed: Int) {
    val allDone: Boolean get() = total > 0 && pending == 0
}

data class AsNeededItem(val medication: Medication, val takenToday: Int, val lastTakenAt: Instant?)

object DoseSlotBuilder {
    /** Minutes before the due time that a dose counts as "due now". */
    const val DUE_WINDOW_MINUTES = 15L

    fun buildDay(
        recipient: CareRecipient,
        medications: List<Medication>,
        entries: List<DoseEntry>,
        date: LocalDate,
        now: Instant,
        settings: ReminderSettings,
    ): List<DoseSlot> {
        val zone = ZoneTime.resolve(recipient.timeZoneId)
        val byKey = HashMap<String, DoseEntry>()
        entries.filter { it.recipientId == recipient.id && !it.isAsNeeded }.sortedBy { it.loggedAt }
            .forEach { byKey[it.occurrenceKey] = it }

        val grouped: SortedMap<LocalDateTime, MutableList<DoseItem>> = java.util.TreeMap()
        for (med in medications.filter { it.recipientId == recipient.id }.sortedBy { it.name.lowercase() }) {
            for (due in ScheduleExpander.expand(med, date, date)) {
                val key = DoseKeys.make(med.id, due)
                val entry = byKey[key]
                val status = statusFor(entry, ZoneTime.toInstant(due, zone), now, settings)
                grouped.getOrPut(due) { ArrayList() }.add(DoseItem(med, key, due, status, entry))
            }
        }
        return grouped.map { (due, items) ->
            DoseSlot(recipient.id, due, ZoneTime.toInstant(due, zone), items, slotStateOf(items))
        }
    }

    fun statusFor(entry: DoseEntry?, dueUtc: Instant, now: Instant, settings: ReminderSettings): DoseStatus {
        if (entry != null && entry.status == DoseStatus.TAKEN) return DoseStatus.TAKEN
        if (entry != null && entry.status == DoseStatus.SKIPPED) return DoseStatus.SKIPPED
        if (now.isBefore(dueUtc.minusSeconds(DUE_WINDOW_MINUTES * 60))) return DoseStatus.UPCOMING
        return if (now.isBefore(dueUtc.plusSeconds(settings.missedAfterMinutes * 60L))) DoseStatus.DUE else DoseStatus.MISSED
    }

    private fun slotStateOf(items: List<DoseItem>): SlotState {
        val pending = items.filter { it.isPending }
        if (pending.isEmpty()) return SlotState.DONE
        if (pending.any { it.status == DoseStatus.MISSED }) return SlotState.MISSED
        return if (pending.any { it.status == DoseStatus.DUE }) SlotState.DUE else SlotState.UPCOMING
    }

    fun summarize(slots: List<DoseSlot>): DaySummary {
        val items = slots.flatMap { it.items }
        return DaySummary(
            items.size,
            items.count { it.status == DoseStatus.TAKEN },
            items.count { it.status == DoseStatus.SKIPPED },
            items.count { it.isPending },
            items.count { it.status == DoseStatus.MISSED },
        )
    }

    fun buildAsNeeded(
        recipient: CareRecipient,
        medications: List<Medication>,
        entries: List<DoseEntry>,
        date: LocalDate,
    ): List<AsNeededItem> {
        val day = date.toString()
        val all = entries.filter { it.recipientId == recipient.id && it.isAsNeeded && it.status == DoseStatus.TAKEN }
        return medications
            .filter { it.recipientId == recipient.id && !it.isArchived && it.kind == ScheduleKind.AS_NEEDED }
            .sortedBy { it.name.lowercase() }
            .map { m ->
                val mine = all.filter { it.medicationId == m.id }
                AsNeededItem(m, mine.count { it.dateKey == day }, mine.maxOfOrNull { it.loggedAt })
            }
    }
}
