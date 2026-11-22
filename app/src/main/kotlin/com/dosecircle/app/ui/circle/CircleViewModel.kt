package com.dosecircle.app.ui.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.core.CareRecipient
import com.dosecircle.core.CircleMember
import com.dosecircle.core.CircleRole
import com.dosecircle.core.Entitlements
import com.dosecircle.core.Feature
import com.dosecircle.core.PlanTier
import com.dosecircle.core.RolePolicy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class PersonUi(
    val id: String,
    val name: String,
    val role: CircleRole,
    val roleLabel: String,
    val colorIndex: Int,
    val isThisDevice: Boolean,
    val reminders: Boolean,
    val missedAlerts: Boolean,
)

data class CircleUi(
    val loading: Boolean = true,
    val recipientName: String = "",
    val people: List<PersonUi> = emptyList(),
    val chain: List<PersonUi> = emptyList(),
    val addableToChain: List<PersonUi> = emptyList(),
    val firstAlertAfter: Int = 30,
    val stepGap: Int = 15,
    val quietEnabled: Boolean = true,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    val use24Hour: Boolean = false,
    val isPro: Boolean = false,
    val canManage: Boolean = false,
)

class CircleViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<CircleUi> = c.changes
        .onStart { emit(Unit) }
        .map { build() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CircleUi())

    private val _paywall = MutableSharedFlow<String>(extraBufferCapacity = 2)
    /** Emits the reason to show on the paywall when a Free user hits a Pro limit. */
    val paywall: SharedFlow<String> = _paywall.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // ----- people -----

    fun addPerson(name: String, role: CircleRole) = launchIo { ctx ->
        if (name.isBlank()) return@launchIo _messages.tryEmit("Enter a name.").let { false }
        val gate = Entitlements.canAddMember(ctx.tier, ctx.members.size, role)
        if (!gate.allowed) return@launchIo _paywall.tryEmit(gate.reason ?: "Pro opens your whole care circle.").let { false }
        val member = CircleMember.create(name, role, false)
        c.store.saveMember(member)
        if (member.receivesMissedAlerts) saveChain(ctx.recipient, ctx.recipient.escalation.chainMemberIds + member.id)
        true
    }

    fun changeRole(id: String, role: CircleRole) = launchIo { ctx ->
        val m = ctx.members.firstOrNull { it.id == id } ?: return@launchIo false
        if (ctx.tier == PlanTier.FREE && !m.isThisDevice && role != CircleRole.VIEWER) {
            _paywall.tryEmit("Letting others log doses is part of Pro."); return@launchIo false
        }
        val updated = m.copy(role = role, receivesDoseReminders = RolePolicy.defaultReceivesDoseReminders(role), receivesMissedAlerts = RolePolicy.defaultReceivesMissedAlerts(role))
        c.store.saveMember(updated)
        syncChainMembership(ctx.recipient, updated)
        true
    }

    fun setReminders(id: String, on: Boolean) = launchIo { ctx ->
        ctx.members.firstOrNull { it.id == id }?.let { c.store.saveMember(it.copy(receivesDoseReminders = on)) }; true
    }

    fun setMissedAlerts(id: String, on: Boolean) = launchIo { ctx ->
        val m = ctx.members.firstOrNull { it.id == id } ?: return@launchIo false
        val updated = m.copy(receivesMissedAlerts = on)
        c.store.saveMember(updated)
        syncChainMembership(ctx.recipient, updated)
        true
    }

    fun remove(id: String) = launchIo { ctx ->
        val m = ctx.members.firstOrNull { it.id == id } ?: return@launchIo false
        if (m.isThisDevice) return@launchIo _messages.tryEmit("You can't remove the person using this phone.").let { false }
        c.store.deleteMember(id)
        saveChain(ctx.recipient, ctx.recipient.escalation.chainMemberIds - id)
        true
    }

    // ----- alert chain -----

    fun moveUp(id: String) = reorder(id, -1)
    fun moveDown(id: String) = reorder(id, +1)

    private fun reorder(id: String, delta: Int) = launchIo { ctx ->
        val chain = ctx.recipient.escalation.chainMemberIds.toMutableList()
        val i = chain.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= chain.size) return@launchIo false
        chain[i] = chain[j].also { chain[j] = chain[i] }
        saveChain(ctx.recipient, chain); true
    }

    fun removeFromChain(id: String) = launchIo { ctx -> saveChain(ctx.recipient, ctx.recipient.escalation.chainMemberIds - id); true }

    fun addToChain(id: String) = launchIo { ctx ->
        val chain = ctx.recipient.escalation.chainMemberIds
        if (id in chain) return@launchIo false
        if (ctx.tier == PlanTier.FREE && chain.isNotEmpty()) {
            _paywall.tryEmit(Entitlements.describe(Feature.ESCALATION_CHAIN)); return@launchIo false
        }
        saveChain(ctx.recipient, chain + id); true
    }

    fun setFirstAlert(minutes: Int) = launchIo { ctx -> c.store.saveRecipient(ctx.recipient.copy(escalation = ctx.recipient.escalation.copy(firstAlertAfterMinutes = minutes))); true }
    fun setStepGap(minutes: Int) = launchIo { ctx -> c.store.saveRecipient(ctx.recipient.copy(escalation = ctx.recipient.escalation.copy(stepGapMinutes = minutes))); true }

    // ----- quiet hours -----

    fun setQuiet(enabled: Boolean? = null, start: Int? = null, end: Int? = null) {
        viewModelScope.launch {
            c.updateSettings { s ->
                s.copy(reminders = s.reminders.copy(
                    quietHoursEnabled = enabled ?: s.reminders.quietHoursEnabled,
                    quietStartMinutes = start ?: s.reminders.quietStartMinutes,
                    quietEndMinutes = end ?: s.reminders.quietEndMinutes,
                ))
            }
            c.reschedule()
        }
    }

    // ----- plumbing -----

    private class Ctx(val recipient: CareRecipient, val members: List<CircleMember>, val tier: PlanTier)

    private fun launchIo(block: (Ctx) -> Boolean) {
        viewModelScope.launch {
            val changed = c.io {
                val s = c.store.settings()
                val rec = c.store.recipients().let { all -> all.firstOrNull { it.id == s.activeRecipientId } ?: all.firstOrNull() } ?: return@io false
                block(Ctx(rec, c.store.members(), s.effectiveTier(Instant.now())))
            }
            if (changed) c.afterDataChange()
        }
    }

    private fun saveChain(rec: CareRecipient, chain: List<String>) {
        c.store.saveRecipient(rec.copy(escalation = rec.escalation.copy(chainMemberIds = chain.distinct())))
    }

    private fun syncChainMembership(rec: CareRecipient, m: CircleMember) {
        val chain = rec.escalation.chainMemberIds
        when {
            m.receivesMissedAlerts && m.id !in chain -> saveChain(rec, chain + m.id)
            !m.receivesMissedAlerts && m.id in chain -> saveChain(rec, chain - m.id)
        }
    }

    private suspend fun build(): CircleUi = c.io {
        val s = c.store.settings()
        val rec = c.store.recipients().let { all -> all.firstOrNull { it.id == s.activeRecipientId } ?: all.firstOrNull() }
        val members = c.store.members()
        val me = members.firstOrNull { it.id == s.thisDeviceMemberId }
        if (rec == null || me == null) return@io CircleUi(loading = false)
        val people = members.mapIndexed { i, m ->
            PersonUi(m.id, m.name, m.role, RolePolicy.label(m.role), i, m.isThisDevice, m.receivesDoseReminders, m.receivesMissedAlerts)
        }
        val chain = rec.escalation.chainMemberIds.mapNotNull { id -> people.firstOrNull { it.id == id } }
        CircleUi(
            loading = false, recipientName = rec.name, people = people, chain = chain,
            addableToChain = people.filter { it.missedAlerts && chain.none { c2 -> c2.id == it.id } },
            firstAlertAfter = rec.escalation.firstAlertAfterMinutes, stepGap = rec.escalation.stepGapMinutes,
            quietEnabled = s.reminders.quietHoursEnabled, quietStart = s.reminders.quietStartMinutes, quietEnd = s.reminders.quietEndMinutes,
            use24Hour = s.use24Hour, isPro = s.effectiveTier(Instant.now()) == PlanTier.PRO, canManage = RolePolicy.canManageCircle(me.role),
        )
    }
}
