package com.dosecircle.core

import java.time.Duration
import java.time.Instant

enum class GuardKind { NONE, ALREADY_LOGGED, TOO_SOON }

data class GuardResult(
    val kind: GuardKind,
    val conflict: DoseEntry? = null,
    val since: Duration? = null,
    val minInterval: Duration? = null,
) {
    companion object { val CLEAR = GuardResult(GuardKind.NONE) }
}

/** Stops the same dose being given twice, whoever is holding the phone. */
object DoseGuard {
    fun minInterval(med: Medication): Duration {
        if (med.minIntervalMinutes > 0) return Duration.ofMinutes(med.minIntervalMinutes.toLong())
        return when (med.kind) {
            ScheduleKind.AS_NEEDED -> Duration.ofHours(4)
            ScheduleKind.EVERY_N_DAYS -> Duration.ofMinutes((maxOf(1, med.intervalDays) * 12 * 60).toLong())
            else -> {
                val t = med.timesOfDayMinutes.distinct().sorted()
                if (t.size <= 1) return Duration.ofHours(8)
                var smallest = Int.MAX_VALUE
                for (i in t.indices) {
                    var gap = t[(i + 1) % t.size] - t[i]
                    if (gap <= 0) gap += 1440
                    smallest = minOf(smallest, gap)
                }
                Duration.ofMinutes(maxOf(60, smallest / 2).toLong())
            }
        }
    }

    fun evaluate(
        med: Medication,
        occurrenceKey: String,
        newStatus: DoseStatus,
        now: Instant,
        existingForKey: DoseEntry?,
        recentEntriesForMed: List<DoseEntry>,
    ): GuardResult {
        if (newStatus != DoseStatus.TAKEN) return GuardResult.CLEAR
        if (existingForKey != null && existingForKey.status == DoseStatus.TAKEN) {
            return GuardResult(GuardKind.ALREADY_LOGGED, existingForKey, Duration.between(existingForKey.loggedAt, now))
        }
        val min = minInterval(med)
        val last = recentEntriesForMed
            .filter { it.status == DoseStatus.TAKEN && it.medicationId == med.id && it.occurrenceKey != occurrenceKey }
            .maxByOrNull { it.loggedAt }
        if (last != null) {
            val since = Duration.between(last.loggedAt, now)
            if (!since.isNegative && since < min) return GuardResult(GuardKind.TOO_SOON, last, since, min)
        }
        return GuardResult.CLEAR
    }
}
