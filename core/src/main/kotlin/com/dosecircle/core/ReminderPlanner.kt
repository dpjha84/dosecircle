package com.dosecircle.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TreeMap

enum class AlertKind { HEADS_UP, REMINDER, NAG, MISSED, REFILL }
enum class AlertChannel { DOSE_REMINDERS, CAREGIVER_ALERTS, REFILLS }

data class PlannedAlert(
    val id: Int,
    val trigger: Instant,
    val kind: AlertKind,
    val recipientId: String,
    val recipientName: String,
    val slotKey: String,
    val dueLocal: LocalDateTime,
    val medicationIds: List<String>,
    val title: String,
    val body: String,
    val critical: Boolean = false,
    val step: Int = 0,
) {
    val channel: AlertChannel
        get() = when (kind) {
            AlertKind.MISSED -> AlertChannel.CAREGIVER_ALERTS
            AlertKind.REFILL -> AlertChannel.REFILLS
            else -> AlertChannel.DOSE_REMINDERS
        }
}

object AlertText {
    private fun poss(name: String) = if (name.endsWith("s") || name.endsWith("S")) "$name'" else "$name's"

    fun reminderTitle(name: String, self: Boolean, clock: String) =
        if (self) "Time for your $clock medicines" else "Time for ${poss(name)} $clock medicines"

    fun headsUpTitle(name: String, self: Boolean, clock: String) =
        if (self) "Your $clock medicines are coming up" else "${poss(name)} $clock medicines are coming up"

    fun nagTitle(name: String, self: Boolean, clock: String) =
        if (self) "Still waiting: your $clock medicines" else "Still waiting: ${poss(name)} $clock medicines"

    fun missedTitle(name: String, clock: String) = "$name hasn't had the $clock medicines"

    fun missedBody(meds: List<String>, dueClock: String, memberClock: String?) =
        meds.joinToString(", ") + ". Due at $dueClock" + (if (memberClock == null) "" else " ($memberClock your time)") +
            ". Open DoseCircle or call to check."

    fun refillTitle(med: String) = "Refill $med soon"

    fun refillBody(person: String, days: Int) =
        if (days <= 0) "$person may be out of this medicine." else "About $days ${if (days == 1) "day" else "days"} left for $person."

    fun guardAlreadyLogged(byName: String, atClock: String) = "$byName already gave this at $atClock."
    fun guardTooSoon(byName: String, atClock: String, since: Duration) =
        "$byName gave this dose at $atClock, only ${TimeText.human(since)} ago."
}

data class PlannerInput(
    val now: Instant,
    val settings: AppSettings,
    val member: CircleMember,
    val members: List<CircleMember>,
    val recipients: List<CareRecipient>,
    val medications: List<Medication>,
    val entries: List<DoseEntry>,
    val tier: PlanTier,
    val horizonHours: Long = 72,
)

/**
 * Pure function: which alarms should exist on THIS phone right now?
 * Every phone in a circle runs the same plan for its own member, so a dose logged anywhere cancels alerts
 * everywhere as soon as the log syncs.
 */
object ReminderPlanner {
    const val MAX_ALERTS = 400

    fun plan(input: PlannerInput): List<PlannedAlert> {
        val now = input.now
        val end = now.plusSeconds(input.horizonHours * 3600)
        val rs = input.settings.reminders
        val use24 = input.settings.use24Hour
        val me = input.member
        val alerts = ArrayList<PlannedAlert>()

        fun add(a: PlannedAlert) {
            if (a.trigger.isAfter(now) && !a.trigger.isAfter(end)) alerts.add(a)
        }

        for (r in input.recipients) {
            val zone = ZoneTime.resolve(r.timeZoneId)
            val first: LocalDate = ZoneTime.today(now, zone).minusDays(1)
            val last: LocalDate = ZoneTime.today(end, zone)
            val logged = input.entries.filter { it.recipientId == r.id && !it.isAsNeeded }.map { it.occurrenceKey }.toHashSet()
            val isSelf = me.role == CircleRole.PATIENT && me.name.trim().equals(r.name.trim(), ignoreCase = true)

            val slots = TreeMap<LocalDateTime, MutableList<Medication>>()
            for (med in input.medications.filter { it.recipientId == r.id }) {
                for (due in ScheduleExpander.expand(med, first, last)) {
                    if (DoseKeys.make(med.id, due) in logged) continue
                    slots.getOrPut(due) { ArrayList() }.add(med)
                }
            }

            for ((due, meds) in slots) {
                val dueUtc = ZoneTime.toInstant(due, zone)
                val clock = TimeText.clock(due, use24)
                val sorted = meds.sortedBy { it.name.lowercase() }
                val names = sorted.map { it.displayName }
                val ids = sorted.map { it.id }
                val critical = meds.any { it.priority == DosePriority.CRITICAL }
                val slotKey = DoseKeys.slot(r.id, due)
                val body = names.joinToString(" · ")

                fun make(kind: AlertKind, trigger: Instant, title: String, text: String, step: Int = 0) = PlannedAlert(
                    id = DoseKeys.stableId("$kind|$slotKey|$step|${me.id}"),
                    trigger = trigger, kind = kind, recipientId = r.id, recipientName = r.name,
                    slotKey = slotKey, dueLocal = due, medicationIds = ids,
                    title = title, body = text, critical = critical, step = step,
                )

                if (me.receivesDoseReminders) {
                    if (rs.headsUpMinutes > 0) {
                        add(make(AlertKind.HEADS_UP, dueUtc.minusSeconds(rs.headsUpMinutes * 60L), AlertText.headsUpTitle(r.name, isSelf, clock), body))
                    }
                    add(make(AlertKind.REMINDER, dueUtc, AlertText.reminderTitle(r.name, isSelf, clock), body))
                    val persistent = rs.style == ReminderStyle.PERSISTENT
                    val count = if (persistent) rs.nagCount.coerceIn(0, 12) else 1
                    val every = if (persistent) maxOf(1, rs.nagIntervalMinutes) else 15
                    for (k in 1..count) {
                        add(make(AlertKind.NAG, dueUtc.plusSeconds(k * every * 60L), AlertText.nagTitle(r.name, isSelf, clock), body, k))
                    }
                }

                if (me.receivesMissedAlerts) {
                    val step = EscalationPlanner.plan(dueUtc, r.escalation, critical, input.tier, input.members, rs)
                        .firstOrNull { it.memberId == me.id }
                    if (step != null) {
                        val memberZone = ZoneTime.resolve(me.timeZoneId)
                        var memberClock: String? = null
                        if (memberZone != zone) {
                            val mine = ZoneTime.toLocal(dueUtc, memberZone)
                            if (mine != due) memberClock = TimeText.clock(mine, use24)
                        }
                        add(make(AlertKind.MISSED, step.trigger, AlertText.missedTitle(r.name, clock), AlertText.missedBody(names, clock, memberClock), step.index))
                    }
                }
            }

            if (Entitlements.has(input.tier, Feature.REFILL_TRACKING) && me.role != CircleRole.AIDE && me.role != CircleRole.VIEWER) {
                val memberZone: ZoneId = ZoneTime.resolve(me.timeZoneId)
                val nowMember = ZoneTime.toLocal(now, memberZone)
                for (med in input.medications.filter { it.recipientId == r.id && !it.isArchived && SupplyCalculator.needsRefill(it) }) {
                    val nine = nowMember.toLocalDate().atStartOfDay().plusHours(9)
                    val at = if (nowMember.isBefore(nine)) nine else nine.plusDays(1)
                    val days = SupplyCalculator.daysRemaining(med) ?: 0
                    add(
                        PlannedAlert(
                            id = DoseKeys.stableId("refill|${med.id}|${at.toLocalDate()}|${me.id}"),
                            trigger = ZoneTime.toInstant(at, memberZone), kind = AlertKind.REFILL,
                            recipientId = r.id, recipientName = r.name, slotKey = "refill|${med.id}", dueLocal = at,
                            medicationIds = listOf(med.id),
                            title = AlertText.refillTitle(med.displayName), body = AlertText.refillBody(r.name, days),
                        ),
                    )
                }
            }
        }
        return alerts.sortedWith(compareBy({ it.trigger }, { it.id })).take(MAX_ALERTS)
    }
}

data class AlertDiff(val toCancel: List<Int>, val toSchedule: List<PlannedAlert>) {
    companion object {
        fun compute(scheduledIds: Collection<Int>, plan: List<PlannedAlert>): AlertDiff {
            val wanted = plan.map { it.id }.toHashSet()
            return AlertDiff(scheduledIds.filter { it !in wanted }.distinct(), plan)
        }
    }
}
