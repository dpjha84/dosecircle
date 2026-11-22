package com.dosecircle.app.ui.onboarding

import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.app.reminders.ReminderHealth
import com.dosecircle.core.Medication
import com.dosecircle.core.OnboardingInput
import com.dosecircle.core.OnboardingPlan
import com.dosecircle.core.OnboardingRole
import com.dosecircle.core.ReminderSettings
import com.dosecircle.core.ReminderStyle
import com.dosecircle.core.ZoneTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

enum class Step { WELCOME, ROLE, NAMES, MEDICINE, REMINDERS, PERMISSIONS, DONE }

val TimePresets = listOf(8 * 60 to "Morning 8:00", 13 * 60 to "Midday 1:00", 18 * 60 to "Evening 6:00", 21 * 60 to "Bedtime 9:00")

data class OnboardingState(
    val step: Step = Step.WELCOME,
    val comfort: Boolean = false,
    val role: OnboardingRole? = null,
    val yourName: String = "",
    val personName: String = "",
    val medName: String = "",
    val strength: String = "",
    val dose: String = "1 tablet",
    val times: Set<Int> = setOf(8 * 60),
    val style: ReminderStyle = ReminderStyle.PERSISTENT,
    val headsUp: Int = 0,
    val alertAfter: Int = 30,
    val use24Hour: Boolean = false,
    val saving: Boolean = false,
) {
    val isSelf: Boolean get() = role == OnboardingRole.SELF

    val canContinue: Boolean
        get() = when (step) {
            Step.ROLE -> role != null
            Step.NAMES -> yourName.isNotBlank() && (isSelf || personName.isNotBlank())
            Step.MEDICINE -> medName.isBlank() || times.isNotEmpty()
            else -> true
        }
}

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(use24Hour = DateFormat.is24HourFormat(c.app)))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _health = MutableStateFlow<ReminderHealth?>(null)
    val health: StateFlow<ReminderHealth?> = _health.asStateFlow()

    val scheduler get() = c.scheduler

    fun edit(change: (OnboardingState) -> OnboardingState) = _state.update(change)

    fun setComfort(on: Boolean) {
        _state.update { it.copy(comfort = on) }
        // Apply immediately so the person sees the larger text on the very next screen.
        viewModelScope.launch { c.updateSettings { it.copy(comfortMode = on) } }
    }

    fun next() = _state.update { s ->
        if (!s.canContinue) s else s.copy(step = Step.values()[minOf(s.step.ordinal + 1, Step.values().lastIndex)])
    }

    fun back() = _state.update { s -> s.copy(step = Step.values()[maxOf(s.step.ordinal - 1, 0)]) }

    fun refreshHealth() { viewModelScope.launch { _health.value = c.io { c.scheduler.health() } } }

    fun sendTest() { viewModelScope.launch { c.io { c.scheduler.sendTest(8) } } }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        val role = s.role ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            c.io {
                val zone = ZoneTime.resolve(null)
                val now = Instant.now()
                val today = ZoneTime.today(now, zone)
                val localMinutes = ZoneTime.toLocal(now, zone).let { it.hour * 60 + it.minute }
                val draft = if (s.medName.isNotBlank() && s.times.isNotEmpty()) {
                    Medication(name = s.medName, strength = s.strength.trim(), doseText = s.dose.trim().ifEmpty { "1 tablet" }, timesOfDayMinutes = s.times.sorted())
                } else null
                val plan = OnboardingPlan.build(
                    OnboardingInput(
                        role = role, yourName = s.yourName, personName = s.personName, firstMedication = draft,
                        reminders = ReminderSettings(style = s.style, headsUpMinutes = s.headsUp),
                        use24Hour = s.use24Hour, comfortMode = s.comfort,
                    ),
                    today,
                )
                // If every chosen time has already passed today, start tomorrow so a brand-new medicine isn't "missed" on day one.
                val allPassed = draft != null && s.times.all { it <= localMinutes }
                val recipient = plan.recipient.copy(escalation = plan.recipient.escalation.copy(firstAlertAfterMinutes = s.alertAfter))
                c.store.saveRecipient(recipient)
                c.store.saveMember(plan.me)
                plan.medication?.let { c.store.saveMedication(if (allPassed) it.copy(startDate = today.plusDays(1)) else it) }
                c.store.saveSettings(plan.settings)
            }
            c.updateSettings { it } // refresh the in-memory copy and notify listeners
            c.reschedule()
            onDone()
        }
    }
}
