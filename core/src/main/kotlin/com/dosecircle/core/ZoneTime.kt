package com.dosecircle.core

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Time zone helpers. java.time already handles DST: a skipped time moves forward by the gap, a repeated time uses the earlier instant. */
object ZoneTime {
    fun resolve(id: String?): ZoneId =
        try {
            if (id.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(id)
        } catch (e: DateTimeException) {
            ZoneId.systemDefault()
        }

    fun toInstant(local: LocalDateTime, zone: ZoneId): Instant = ZonedDateTime.ofLocal(local, zone, null).toInstant()
    fun toLocal(instant: Instant, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(instant, zone)
    fun today(now: Instant, zone: ZoneId): LocalDate = toLocal(now, zone).toLocalDate()
}

object DoseKeys {
    private val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.ROOT)

    fun make(medicationId: String, dueLocal: LocalDateTime) = "$medicationId|${dueLocal.format(stamp)}"
    fun makeAsNeeded(medicationId: String, now: Instant) = "$medicationId|prn|${now.toEpochMilli()}"
    fun slot(recipientId: String, dueLocal: LocalDateTime) = "$recipientId|${dueLocal.format(stamp)}"

    /** Stable across processes and runs (String.hashCode is stable on the JVM, but we pin the algorithm so it never changes). */
    fun stableId(text: String): Int {
        var hash = 2166136261L
        for (c in text) {
            hash = (hash xor c.code.toLong()) * 16777619L and 0xFFFFFFFFL
        }
        val id = (hash and 0x7FFFFFFFL).toInt()
        return if (id == 0) 1 else id
    }
}

object TimeText {
    /** Manual formatting: newer JDKs put a narrow no-break space before AM/PM, which looks wrong in notifications. */
    fun clock(minutes: Int, use24h: Boolean): String {
        val m = minutes.coerceIn(0, 1439)
        val h = m / 60
        val min = m % 60
        if (use24h) return "%02d:%02d".format(Locale.ROOT, h, min)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(Locale.ROOT, h12, min, if (h < 12) "AM" else "PM")
    }

    fun clock(local: LocalDateTime, use24h: Boolean) = clock(local.hour * 60 + local.minute, use24h)
    fun clock(local: LocalTime, use24h: Boolean) = clock(local.hour * 60 + local.minute, use24h)

    fun dayLabel(date: LocalDate, today: LocalDate): String {
        val diff = today.toEpochDay() - date.toEpochDay()
        return when (diff) {
            0L -> "Today"
            1L -> "Yesterday"
            -1L -> "Tomorrow"
            else -> {
                val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                val mon = date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                "$dow, $mon ${date.dayOfMonth}"
            }
        }
    }

    fun greeting(local: LocalDateTime) = when {
        local.hour < 5 -> "Good evening"
        local.hour < 12 -> "Good morning"
        local.hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    fun period(local: LocalDateTime) = when {
        local.hour < 5 -> "Night"
        local.hour < 12 -> "Morning"
        local.hour < 17 -> "Afternoon"
        local.hour < 21 -> "Evening"
        else -> "Night"
    }

    fun human(d: java.time.Duration): String {
        val minutes = Math.round(d.seconds / 60.0).toInt()
        if (minutes < 1) return "moments"
        if (minutes < 60) return if (minutes == 1) "1 minute" else "$minutes minutes"
        val h = minutes / 60
        val m = minutes % 60
        val hours = if (h == 1) "1 hour" else "$h hours"
        return if (m == 0) hours else "$hours $m min"
    }
}
