package com.dosecircle.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.app.reminders.ReminderHealth
import com.dosecircle.core.AppSettings
import com.dosecircle.core.PlanTier
import com.dosecircle.core.ReminderStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class SettingsUi(val settings: AppSettings = AppSettings(), val isPro: Boolean = false)

class SettingsViewModel(val c: AppContainer) : ViewModel() {
    val ui: StateFlow<SettingsUi> = c.settings
        .map { SettingsUi(it, it.effectiveTier(Instant.now()) == PlanTier.PRO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi(c.settings.value, c.tier() == PlanTier.PRO))

    private val _health = MutableStateFlow<ReminderHealth?>(null)
    val health: StateFlow<ReminderHealth?> = _health.asStateFlow()

    fun refreshHealth() { viewModelScope.launch { _health.value = c.io { c.scheduler.health() } } }

    private fun reminders(change: (com.dosecircle.core.ReminderSettings) -> com.dosecircle.core.ReminderSettings) {
        viewModelScope.launch {
            c.updateSettings { it.copy(reminders = change(it.reminders)) }
            c.reschedule()
        }
    }

    fun setStyle(style: ReminderStyle) = reminders { it.copy(style = style) }
    fun setHeadsUp(minutes: Int) = reminders { it.copy(headsUpMinutes = minutes) }
    fun setSnooze(minutes: Int) = reminders { it.copy(snoozeMinutes = minutes) }
    fun setMissedAfter(minutes: Int) = reminders { it.copy(missedAfterMinutes = minutes) }

    fun setComfort(on: Boolean) { viewModelScope.launch { c.updateSettings { it.copy(comfortMode = on) } } }
    fun set24Hour(on: Boolean) { viewModelScope.launch { c.updateSettings { it.copy(use24Hour = on) }; c.reschedule() } }

    fun sendTest() { viewModelScope.launch { c.io { c.scheduler.sendTest(8) } } }

    fun eraseEverything() { viewModelScope.launch { c.eraseEverything() } }

    /** Debug builds only: flips Pro locally so the paid features can be tested without Play. */
    fun debugTogglePro() { viewModelScope.launch { c.billing.setPro(c.tier() != PlanTier.PRO) } }

    fun refreshEntitlement(onDone: (Boolean) -> Unit) { viewModelScope.launch { onDone(c.billing.refreshEntitlement()) } }
}
