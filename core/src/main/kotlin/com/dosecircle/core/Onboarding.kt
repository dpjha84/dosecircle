package com.dosecircle.core

import java.time.LocalDate

enum class OnboardingRole { SELF, FAMILY_CAREGIVER, AIDE }

data class OnboardingInput(
    val role: OnboardingRole,
    val yourName: String = "",
    val personName: String? = null,
    val timeZoneId: String? = null,
    val firstMedication: Medication? = null,
    val reminders: ReminderSettings = ReminderSettings(),
    val use24Hour: Boolean = false,
    val comfortMode: Boolean = false,
)

data class OnboardingResult(
    val recipient: CareRecipient,
    val me: CircleMember,
    val medication: Medication?,
    val settings: AppSettings,
)

object OnboardingPlan {
    fun build(input: OnboardingInput, today: LocalDate): OnboardingResult {
        val tz = input.timeZoneId ?: java.time.ZoneId.systemDefault().id
        val yourName = input.yourName.trim().ifEmpty { "Me" }
        val role = when (input.role) {
            OnboardingRole.SELF -> CircleRole.PATIENT
            OnboardingRole.FAMILY_CAREGIVER -> CircleRole.PRIMARY_CAREGIVER
            OnboardingRole.AIDE -> CircleRole.AIDE
        }
        val personName = if (input.role == OnboardingRole.SELF) yourName else input.personName?.trim().orEmpty().ifEmpty { "Loved one" }

        var me = CircleMember.create(yourName, role, true, tz)
        // A family caregiver who sets this up usually gives the medicines too, so they get the reminder as well as the missed-dose alert.
        if (input.role == OnboardingRole.FAMILY_CAREGIVER) me = me.copy(receivesDoseReminders = true)

        val recipient = CareRecipient(
            name = personName, timeZoneId = tz,
            escalation = EscalationPolicy(chainMemberIds = if (me.receivesMissedAlerts) listOf(me.id) else emptyList()),
        )
        val med = input.firstMedication?.takeIf { it.name.isNotBlank() }?.let {
            it.copy(recipientId = recipient.id, name = it.name.trim(), startDate = today)
        }
        return OnboardingResult(
            recipient, me, med,
            AppSettings(
                reminders = input.reminders, thisDeviceMemberId = me.id, activeRecipientId = recipient.id,
                lastLoggerMemberId = me.id, onboardingDone = true, use24Hour = input.use24Hour, comfortMode = input.comfortMode,
            ),
        )
    }
}
