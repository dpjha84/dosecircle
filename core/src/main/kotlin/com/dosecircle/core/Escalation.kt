package com.dosecircle.core

import java.time.Instant
import java.time.ZoneId

data class EscalationStep(val memberId: String, val index: Int, val trigger: Instant)

object QuietHours {
    /** Moves a trigger that falls inside quiet hours (in the member's own zone) to when quiet hours end. */
    fun defer(trigger: Instant, zone: ZoneId, startMinutes: Int, endMinutes: Int): Instant {
        if (startMinutes == endMinutes) return trigger
        val local = ZoneTime.toLocal(trigger, zone)
        val m = local.hour * 60 + local.minute
        val overnight = startMinutes > endMinutes
        val quiet = if (overnight) (m >= startMinutes || m < endMinutes) else (m >= startMinutes && m < endMinutes)
        if (!quiet) return trigger
        val day = local.toLocalDate()
        val endDay = if (!overnight || m < endMinutes) day else day.plusDays(1)
        val end = endDay.atStartOfDay().plusMinutes(endMinutes.toLong())
        return ZoneTime.toInstant(end, zone)
    }
}

object EscalationPlanner {
    fun plan(
        due: Instant,
        policy: EscalationPolicy,
        critical: Boolean,
        tier: PlanTier,
        members: List<CircleMember>,
        settings: ReminderSettings,
    ): List<EscalationStep> {
        val factor = if (critical) policy.criticalSpeedFactor.coerceIn(0.1, 1.0) else 1.0
        val chain = policy.chainMemberIds
            .mapNotNull { id -> members.firstOrNull { it.id == id } }
            .filter { it.receivesMissedAlerts }
            .take(Entitlements.maxEscalationSteps(tier))

        return chain.mapIndexed { i, member ->
            val minutes = Math.round((policy.firstAlertAfterMinutes + i * policy.stepGapMinutes) * factor)
            var trigger = due.plusSeconds(minutes * 60)
            if (!critical && settings.quietHoursEnabled) {
                trigger = QuietHours.defer(trigger, ZoneTime.resolve(member.timeZoneId), settings.quietStartMinutes, settings.quietEndMinutes)
            }
            EscalationStep(member.id, i, trigger)
        }
    }
}
