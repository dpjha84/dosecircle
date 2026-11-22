package com.dosecircle.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

enum class CircleRole { PATIENT, PRIMARY_CAREGIVER, CAREGIVER, AIDE, VIEWER }
enum class MedForm { TABLET, CAPSULE, LIQUID, DROPS, INJECTION, INHALER, PATCH, CREAM, OTHER }
enum class ScheduleKind { DAILY, DAYS_OF_WEEK, EVERY_N_DAYS, AS_NEEDED }
enum class DosePriority { NORMAL, CRITICAL }
enum class DoseStatus { UPCOMING, DUE, MISSED, TAKEN, SKIPPED }
enum class ReminderStyle { GENTLE, PERSISTENT }
enum class PlanTier { FREE, PRO }
enum class Feature { MULTIPLE_RECIPIENTS, CO_LOGGING, ESCALATION_CHAIN, REFILL_TRACKING, DOCTOR_REPORT, CLOUD_SYNC }

object Ids {
    fun new(): String = UUID.randomUUID().toString().replace("-", "")
}

data class EscalationPolicy(
    val firstAlertAfterMinutes: Int = 30,
    val stepGapMinutes: Int = 15,
    val criticalSpeedFactor: Double = 0.5,
    val chainMemberIds: List<String> = emptyList(),
)

data class CareRecipient(
    val id: String = Ids.new(),
    val name: String = "",
    val timeZoneId: String = ZoneId.systemDefault().id,
    val escalation: EscalationPolicy = EscalationPolicy(),
)

data class CircleMember(
    val id: String = Ids.new(),
    val name: String = "",
    val role: CircleRole = CircleRole.CAREGIVER,
    val timeZoneId: String = ZoneId.systemDefault().id,
    val isThisDevice: Boolean = false,
    val receivesDoseReminders: Boolean = false,
    val receivesMissedAlerts: Boolean = false,
) {
    companion object {
        fun create(name: String, role: CircleRole, thisDevice: Boolean, timeZoneId: String? = null) = CircleMember(
            name = name.trim(),
            role = role,
            isThisDevice = thisDevice,
            timeZoneId = timeZoneId ?: ZoneId.systemDefault().id,
            receivesDoseReminders = RolePolicy.defaultReceivesDoseReminders(role),
            receivesMissedAlerts = RolePolicy.defaultReceivesMissedAlerts(role),
        )
    }
}

object RolePolicy {
    fun canLogDoses(r: CircleRole) = r != CircleRole.VIEWER
    fun canEditMedications(r: CircleRole) = r == CircleRole.PATIENT || r == CircleRole.PRIMARY_CAREGIVER
    fun canManageCircle(r: CircleRole) = r == CircleRole.PATIENT || r == CircleRole.PRIMARY_CAREGIVER
    fun defaultReceivesDoseReminders(r: CircleRole) = r == CircleRole.PATIENT || r == CircleRole.AIDE
    fun defaultReceivesMissedAlerts(r: CircleRole) =
        r == CircleRole.PRIMARY_CAREGIVER || r == CircleRole.CAREGIVER || r == CircleRole.VIEWER

    fun label(r: CircleRole) = when (r) {
        CircleRole.PATIENT -> "Takes the medicines"
        CircleRole.PRIMARY_CAREGIVER -> "Main caregiver"
        CircleRole.CAREGIVER -> "Family caregiver"
        CircleRole.AIDE -> "Home aide"
        CircleRole.VIEWER -> "Can view only"
    }
}

data class Medication(
    val id: String = Ids.new(),
    val recipientId: String = "",
    val name: String = "",
    val strength: String = "",
    val form: MedForm = MedForm.TABLET,
    val doseText: String = "1 tablet",
    val instructions: String = "",
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val timesOfDayMinutes: List<Int> = listOf(8 * 60),
    /** Bit per weekday, Sunday = bit 0. 127 = every day. */
    val daysOfWeekMask: Int = 127,
    val intervalDays: Int = 1,
    val startDate: LocalDate = LocalDate.of(2000, 1, 1),
    val endDate: LocalDate? = null,
    val priority: DosePriority = DosePriority.NORMAL,
    /** 0 = automatic, see DoseGuard.minInterval. */
    val minIntervalMinutes: Int = 0,
    val unitsPerDose: Double = 1.0,
    val unitsRemaining: Double? = null,
    val refillThresholdDays: Int = 7,
    val colorIndex: Int = 0,
    val isArchived: Boolean = false,
) {
    val displayName: String get() = if (strength.isBlank()) name else "$name $strength"
}

data class DoseEntry(
    val id: String = Ids.new(),
    val recipientId: String = "",
    val medicationId: String = "",
    val occurrenceKey: String = "",
    val dueLocal: LocalDateTime = LocalDateTime.of(2000, 1, 1, 0, 0),
    /** Only TAKEN or SKIPPED are ever stored. */
    val status: DoseStatus = DoseStatus.TAKEN,
    val loggedAt: Instant = Instant.EPOCH,
    val loggedByMemberId: String = "",
    val note: String? = null,
    val isAsNeeded: Boolean = false,
) {
    val dateKey: String get() = dueLocal.toLocalDate().toString()
}

data class ReminderSettings(
    val style: ReminderStyle = ReminderStyle.PERSISTENT,
    val headsUpMinutes: Int = 0,
    val snoozeMinutes: Int = 10,
    val nagIntervalMinutes: Int = 5,
    val nagCount: Int = 6,
    val missedAfterMinutes: Int = 60,
    val quietHoursEnabled: Boolean = true,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
)

data class AppSettings(
    val reminders: ReminderSettings = ReminderSettings(),
    val tier: PlanTier = PlanTier.FREE,
    val proExpires: Instant? = null,
    val thisDeviceMemberId: String? = null,
    val activeRecipientId: String? = null,
    val lastLoggerMemberId: String? = null,
    val onboardingDone: Boolean = false,
    val use24Hour: Boolean = false,
    val comfortMode: Boolean = false,
) {
    fun effectiveTier(now: Instant): PlanTier =
        if (tier == PlanTier.PRO && (proExpires == null || proExpires.isAfter(now))) PlanTier.PRO else PlanTier.FREE
}

data class GateResult(val allowed: Boolean, val feature: Feature? = null, val reason: String? = null) {
    companion object { val OK = GateResult(true) }
}

/** Single source of truth for what Free and Pro include. */
object Entitlements {
    const val FREE_MAX_MEMBERS = 2
    const val PRO_MAX_MEMBERS = 12
    const val PRO_MAX_RECIPIENTS = 25
    const val PRO_MAX_ESCALATION_STEPS = 5

    fun has(tier: PlanTier, @Suppress("UNUSED_PARAMETER") feature: Feature) = tier == PlanTier.PRO
    fun maxRecipients(tier: PlanTier) = if (tier == PlanTier.PRO) PRO_MAX_RECIPIENTS else 1
    fun maxCircleMembers(tier: PlanTier) = if (tier == PlanTier.PRO) PRO_MAX_MEMBERS else FREE_MAX_MEMBERS
    fun maxEscalationSteps(tier: PlanTier) = if (tier == PlanTier.PRO) PRO_MAX_ESCALATION_STEPS else 1

    fun canAddRecipient(tier: PlanTier, currentCount: Int) =
        if (currentCount < maxRecipients(tier)) GateResult.OK
        else GateResult(false, Feature.MULTIPLE_RECIPIENTS, "Pro lets you look after more than one person.")

    /** Free: you plus one person who can view. Co-logging is Pro. The first member (this phone's owner) can have any role. */
    fun canAddMember(tier: PlanTier, currentCount: Int, role: CircleRole): GateResult {
        if (currentCount >= maxCircleMembers(tier)) {
            return GateResult(
                false, Feature.CO_LOGGING,
                if (tier == PlanTier.FREE) "Free includes you and one family member who can view. Pro adds your whole care circle."
                else "Your circle is full.",
            )
        }
        if (tier == PlanTier.FREE && currentCount >= 1 && role != CircleRole.VIEWER) {
            return GateResult(false, Feature.CO_LOGGING, "Letting others log doses is part of Pro.")
        }
        return GateResult.OK
    }

    fun canUse(tier: PlanTier, feature: Feature) =
        if (has(tier, feature)) GateResult.OK else GateResult(false, feature, describe(feature))

    fun describe(f: Feature) = when (f) {
        Feature.MULTIPLE_RECIPIENTS -> "Look after more than one person."
        Feature.CO_LOGGING -> "Let family and aides log doses with names attached."
        Feature.ESCALATION_CHAIN -> "Alert a chain of caregivers until someone responds."
        Feature.REFILL_TRACKING -> "Know before medicines run out."
        Feature.DOCTOR_REPORT -> "Share a clear PDF report with the doctor."
        Feature.CLOUD_SYNC -> "Keep every phone in the circle in sync."
    }

    fun clampChain(tier: PlanTier, chain: List<String>) = chain.distinct().take(maxEscalationSteps(tier))
}
