package com.dosecircle.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object ScheduleExpander {
    /** Local wall-clock due times between two dates, inclusive. */
    fun expand(med: Medication, from: LocalDate, to: LocalDate): List<LocalDateTime> {
        // A stopped medicine keeps its history through endDate; without one it has no doses at all.
        if (med.kind == ScheduleKind.AS_NEEDED || (med.isArchived && med.endDate == null)) return emptyList()
        val times = med.timesOfDayMinutes.filter { it in 0..1439 }.distinct().sorted()
        if (times.isEmpty()) return emptyList()
        val interval = maxOf(1, med.intervalDays)
        val out = ArrayList<LocalDateTime>()
        var d = from
        while (!d.isAfter(to)) {
            if (med.endDate != null && d.isAfter(med.endDate)) break
            if (!d.isBefore(med.startDate) && appliesOn(med, d, interval)) {
                for (m in times) out.add(LocalDateTime.of(d, LocalTime.of(m / 60, m % 60)))
            }
            d = d.plusDays(1)
        }
        return out
    }

    private fun appliesOn(med: Medication, d: LocalDate, interval: Int): Boolean = when (med.kind) {
        ScheduleKind.DAILY -> true
        ScheduleKind.DAYS_OF_WEEK -> ((med.daysOfWeekMask shr (d.dayOfWeek.value % 7)) and 1) == 1
        ScheduleKind.EVERY_N_DAYS -> Math.floorMod(d.toEpochDay() - med.startDate.toEpochDay(), interval.toLong()) == 0L
        ScheduleKind.AS_NEEDED -> false
    }

    fun describe(med: Medication, use24h: Boolean): String {
        if (med.kind == ScheduleKind.AS_NEEDED) return "Only when needed"
        val times = med.timesOfDayMinutes.distinct().sorted().joinToString(", ") { TimeText.clock(it, use24h) }
        val days = when (med.kind) {
            ScheduleKind.DAILY -> "Every day"
            ScheduleKind.EVERY_N_DAYS -> if (med.intervalDays <= 1) "Every day" else "Every ${med.intervalDays} days"
            else -> daysText(med.daysOfWeekMask)
        }
        return "$days at $times"
    }

    fun daysText(mask: Int): String {
        if ((mask and 127) == 127) return "Every day"
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val chosen = listOf(1, 2, 3, 4, 5, 6, 0).filter { ((mask shr it) and 1) == 1 }.map { names[it] }
        return if (chosen.isEmpty()) "No days" else chosen.joinToString(", ")
    }
}

object SupplyCalculator {
    fun dosesPerDay(med: Medication): Double? {
        val times = med.timesOfDayMinutes.distinct().size
        return when (med.kind) {
            ScheduleKind.DAILY -> times.toDouble()
            ScheduleKind.DAYS_OF_WEEK -> times * ((0..6).count { ((med.daysOfWeekMask shr it) and 1) == 1 } / 7.0)
            ScheduleKind.EVERY_N_DAYS -> times / maxOf(1, med.intervalDays).toDouble()
            ScheduleKind.AS_NEEDED -> null
        }
    }

    fun daysRemaining(med: Medication): Int? {
        val remaining = med.unitsRemaining ?: return null
        if (med.unitsPerDose <= 0) return null
        val perDay = dosesPerDay(med) ?: return null
        if (perDay <= 0) return null
        return Math.floor(maxOf(0.0, remaining) / (med.unitsPerDose * perDay)).toInt()
    }

    fun needsRefill(med: Medication): Boolean = daysRemaining(med)?.let { it <= med.refillThresholdDays } ?: false
}
