package com.dosecircle.app.reminders

import com.dosecircle.core.AlertText
import com.dosecircle.core.AppSettings
import com.dosecircle.core.CareRecipient
import com.dosecircle.core.CircleMember
import com.dosecircle.core.GuardKind
import com.dosecircle.core.GuardResult
import com.dosecircle.core.Medication
import com.dosecircle.core.TimeText
import com.dosecircle.core.ZoneTime

object GuardMessages {
    /** "Metformin 500 mg: Maria already gave this at 1:02 PM." */
    fun describe(
        med: Medication, guard: GuardResult, recipient: CareRecipient?, members: List<CircleMember>, settings: AppSettings,
    ): String {
        val conflict = guard.conflict ?: return med.displayName
        val who = members.firstOrNull { it.id == conflict.loggedByMemberId }?.name ?: "Someone"
        val zone = ZoneTime.resolve(recipient?.timeZoneId)
        val clock = TimeText.clock(ZoneTime.toLocal(conflict.loggedAt, zone), settings.use24Hour)
        val text = when (guard.kind) {
            GuardKind.ALREADY_LOGGED -> AlertText.guardAlreadyLogged(who, clock)
            GuardKind.TOO_SOON -> AlertText.guardTooSoon(who, clock, guard.since ?: java.time.Duration.ZERO)
            GuardKind.NONE -> ""
        }
        return "${med.displayName}: $text"
    }
}
