package com.dosecircle.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.app.reminders.GuardMessages
import com.dosecircle.core.AsNeededItem
import com.dosecircle.core.CircleRole
import com.dosecircle.core.DoseKeys
import com.dosecircle.core.DoseSlotBuilder
import com.dosecircle.core.DoseStatus
import com.dosecircle.core.LogRequest
import com.dosecircle.core.RolePolicy
import com.dosecircle.core.SlotState
import com.dosecircle.core.TimeText
import com.dosecircle.core.ZoneTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

data class MemberUi(val id: String, val name: String, val colorIndex: Int)
data class RecipientUi(val id: String, val name: String)

data class ItemUi(
    val slotKey: String,
    val medId: String,
    val key: String,
    val dueLocal: LocalDateTime,
    val name: String,
    val strength: String,
    val dose: String,
    val instructions: String,
    val colorIndex: Int,
    val status: DoseStatus,
    val statusText: String,
    val critical: Boolean,
)

data class SlotUi(
    val key: String,
    val time: String,
    val period: String,
    val state: SlotState,
    val stateText: String,
    val items: List<ItemUi>,
    val pending: Int,
    val doneSummary: String,
)

data class AsNeededUi(val medId: String, val name: String, val strength: String, val dose: String, val summary: String, val colorIndex: Int)

data class TodayUi(
    val loading: Boolean = true,
    val greeting: String = "",
    val subtitle: String = "",
    val progress: Float = 0f,
    val progressText: String = "",
    val allDone: Boolean = false,
    val slots: List<SlotUi> = emptyList(),
    val asNeeded: List<AsNeededUi> = emptyList(),
    val loggers: List<MemberUi> = emptyList(),
    val loggerId: String? = null,
    val recipients: List<RecipientUi> = emptyList(),
    val activeRecipientId: String? = null,
    val hasMedicines: Boolean = false,
    val canEditMedicines: Boolean = false,
    val nextIndex: Int = -1,
)

sealed interface TodayEvent {
    data class Logged(val message: String, val entryIds: List<String>) : TodayEvent
    data class Message(val text: String) : TodayEvent
}

data class ConfirmUi(val message: String, val items: List<ItemUi>, val status: DoseStatus, val asNeeded: AsNeededUi? = null)

class TodayViewModel(private val c: AppContainer) : ViewModel() {

    private val ticker = flow { while (true) { delay(30_000); emit(Unit) } }

    val ui: StateFlow<TodayUi> = merge(c.changes, ticker)
        .onStart { emit(Unit) }
        .conflate()
        .map { build() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    private val _events = MutableSharedFlow<TodayEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TodayEvent> = _events.asSharedFlow()

    private val _confirm = MutableStateFlow<ConfirmUi?>(null)
    val confirm: StateFlow<ConfirmUi?> = _confirm.asStateFlow()

    // ----- actions -----

    fun take(items: List<ItemUi>) = perform(items, DoseStatus.TAKEN, override = false)
    fun skip(items: List<ItemUi>) = perform(items, DoseStatus.SKIPPED, override = false)

    fun logAsNeeded(item: AsNeededUi) = logAsNeeded(item, override = false)

    private fun logAsNeeded(item: AsNeededUi, override: Boolean) {
        viewModelScope.launch {
            val loggerId = loggerId() ?: return@launch
            val now = Instant.now()
            val outcome = c.io {
                val settings = c.store.settings()
                val members = c.store.members()
                val rec = c.store.recipients().firstOrNull { it.id == settings.activeRecipientId }
                val localNow = ZoneTime.toLocal(now, ZoneTime.resolve(rec?.timeZoneId))
                val res = c.doseService.log(
                    LogRequest(item.medId, DoseKeys.makeAsNeeded(item.medId, now), localNow, DoseStatus.TAKEN, loggerId, override, asNeeded = true),
                )
                val med = c.store.medication(item.medId)
                val text = if (res.needsConfirmation && med != null) GuardMessages.describe(med, res.guard, rec, members, settings) else null
                Triple(res, text, localNow)
            }
            val (res, text, _) = outcome
            when {
                res.success -> { c.afterDataChange(); _events.emit(TodayEvent.Logged("Logged ${item.name}", listOf(res.entry!!.id))) }
                res.needsConfirmation -> _confirm.value = ConfirmUi(text ?: item.name, emptyList(), DoseStatus.TAKEN, item)
                else -> _events.emit(TodayEvent.Message(res.error ?: "Couldn't log that dose."))
            }
        }
    }

    fun confirmOverride() {
        val pending = _confirm.value ?: return
        _confirm.value = null
        if (pending.asNeeded != null) logAsNeeded(pending.asNeeded, override = true)
        else perform(pending.items, pending.status, override = true)
    }

    fun dismissConfirm() { _confirm.value = null }

    fun undo(entryIds: List<String>) {
        viewModelScope.launch {
            val who = loggerId() ?: return@launch
            c.io { entryIds.forEach { c.doseService.undo(it, who) } }
            c.afterDataChange()
        }
    }

    fun selectLogger(id: String) { viewModelScope.launch { c.updateSettings { it.copy(lastLoggerMemberId = id) } } }
    fun selectRecipient(id: String) { viewModelScope.launch { c.updateSettings { it.copy(activeRecipientId = id) }; c.reschedule() } }

    private data class Outcome(val logged: List<String>, val conflicts: List<ItemUi>, val conflictText: List<String>, val error: String?)

    private fun perform(items: List<ItemUi>, status: DoseStatus, override: Boolean) {
        viewModelScope.launch {
            val loggerId = loggerId()
            if (loggerId == null) { _events.emit(TodayEvent.Message("Choose who is logging this dose.")); return@launch }
            val outcome = c.io {
                val settings = c.store.settings()
                val members = c.store.members()
                val rec = c.store.recipients().firstOrNull { it.id == settings.activeRecipientId }
                val logged = ArrayList<String>()
                val conflicts = ArrayList<ItemUi>()
                val texts = ArrayList<String>()
                var error: String? = null
                for (item in items) {
                    val res = c.doseService.log(LogRequest(item.medId, item.key, item.dueLocal, status, loggerId, override))
                    when {
                        res.success -> logged.add(res.entry!!.id)
                        res.needsConfirmation -> {
                            conflicts.add(item)
                            c.store.medication(item.medId)?.let { texts.add(GuardMessages.describe(it, res.guard, rec, members, settings)) }
                        }
                        else -> error = res.error
                    }
                }
                items.map { it.slotKey }.distinct().forEach { c.scheduler.dismissSlot(it) }
                Outcome(logged, conflicts, texts, error)
            }
            if (outcome.logged.isNotEmpty()) {
                c.afterDataChange()
                val verb = if (status == DoseStatus.TAKEN) "Logged" else "Skipped"
                val what = if (outcome.logged.size == 1) items.first().name else "${outcome.logged.size} medicines"
                _events.emit(TodayEvent.Logged("$verb $what", outcome.logged))
            }
            if (outcome.conflicts.isNotEmpty()) {
                _confirm.value = ConfirmUi(outcome.conflictText.joinToString("\n\n"), outcome.conflicts, status)
            }
            outcome.error?.let { _events.emit(TodayEvent.Message(it)) }
        }
    }

    private suspend fun loggerId(): String? = c.io {
        val s = c.store.settings()
        val loggable = c.store.members().filter { RolePolicy.canLogDoses(it.role) }
        (loggable.firstOrNull { it.id == s.lastLoggerMemberId } ?: loggable.firstOrNull { it.id == s.thisDeviceMemberId } ?: loggable.firstOrNull())?.id
    }

    // ----- state -----

    private suspend fun build(): TodayUi = c.io {
        val settings = c.store.settings()
        val members = c.store.members()
        val recipients = c.store.recipients()
        val rec = recipients.firstOrNull { it.id == settings.activeRecipientId } ?: recipients.firstOrNull()
        val me = members.firstOrNull { it.id == settings.thisDeviceMemberId }
        if (rec == null || me == null) return@io TodayUi(loading = false)

        val now = Instant.now()
        val zone = ZoneTime.resolve(rec.timeZoneId)
        val localNow = ZoneTime.toLocal(now, zone)
        val today = localNow.toLocalDate()
        val meds = c.store.medications(rec.id, false)
        val entries = c.store.entries(rec.id, today, today)
        val slots = DoseSlotBuilder.buildDay(rec, meds, entries, today, now, settings.reminders)
        val summary = DoseSlotBuilder.summarize(slots)
        val names = members.associate { it.id to it.name }
        val use24 = settings.use24Hour

        fun logText(verb: String, e: com.dosecircle.core.DoseEntry): String {
            val who = names[e.loggedByMemberId] ?: "someone"
            return "$verb by $who · ${TimeText.clock(ZoneTime.toLocal(e.loggedAt, zone), use24)}"
        }

        val slotUi = slots.map { s ->
            val items = s.items.map { i ->
                ItemUi(
                    slotKey = s.slotKey, medId = i.medication.id, key = i.key, dueLocal = i.dueLocal,
                    name = i.medication.name, strength = i.medication.strength, dose = i.medication.doseText,
                    instructions = i.medication.instructions, colorIndex = i.medication.colorIndex, status = i.status,
                    statusText = when (i.status) {
                        DoseStatus.TAKEN -> i.entry?.let { logText("Taken", it) } ?: "Taken"
                        DoseStatus.SKIPPED -> i.entry?.let { logText("Skipped", it) } ?: "Skipped"
                        else -> ""
                    },
                    critical = i.medication.priority == com.dosecircle.core.DosePriority.CRITICAL,
                )
            }
            val stateText = when (s.state) {
                SlotState.DUE -> "Due now"
                SlotState.MISSED -> "Missed · ${TimeText.human(Duration.between(s.dueUtc, now))} ago"
                SlotState.UPCOMING -> "In ${TimeText.human(Duration.between(now, s.dueUtc))}"
                SlotState.DONE -> "All done"
            }
            val doneSummary = items.joinToString(", ") { it.name } +
                (s.items.firstOrNull { it.entry != null }?.entry?.let { "\n" + logText(if (it.status == DoseStatus.SKIPPED) "Skipped" else "Taken", it) } ?: "")
            SlotUi(s.slotKey, TimeText.clock(s.dueLocal, use24), TimeText.period(s.dueLocal), s.state, stateText, items, s.pendingCount, doneSummary)
        }

        val asNeeded = DoseSlotBuilder.buildAsNeeded(rec, meds, entries, today).map { it.toUi(zone, use24) }
        val loggers = members.filter { RolePolicy.canLogDoses(it.role) }.mapIndexed { i, m -> MemberUi(m.id, m.name, i) }
        val doneCount = summary.taken + summary.skipped
        val progressText = when {
            summary.total == 0 -> "Nothing scheduled today"
            else -> buildString {
                append("${summary.taken} of ${summary.total} taken")
                if (summary.missed > 0) append(" · ${summary.missed} missed")
                if (summary.skipped > 0) append(" · ${summary.skipped} skipped")
            }
        }

        TodayUi(
            loading = false,
            greeting = "${TimeText.greeting(localNow)}, ${me.name}",
            subtitle = if (me.role == CircleRole.PATIENT && me.name.trim().equals(rec.name.trim(), true)) "Here's your day" else "${rec.name}'s medicines today",
            progress = if (summary.total == 0) 0f else doneCount.toFloat() / summary.total,
            progressText = progressText,
            allDone = summary.allDone,
            slots = slotUi,
            asNeeded = asNeeded,
            loggers = loggers,
            loggerId = (loggers.firstOrNull { it.id == settings.lastLoggerMemberId } ?: loggers.firstOrNull { it.id == me.id } ?: loggers.firstOrNull())?.id,
            recipients = recipients.map { RecipientUi(it.id, it.name) },
            activeRecipientId = rec.id,
            hasMedicines = meds.isNotEmpty(),
            canEditMedicines = RolePolicy.canEditMedications(me.role),
            nextIndex = slotUi.indexOfFirst { it.state != SlotState.DONE },
        )
    }

    private fun AsNeededItem.toUi(zone: java.time.ZoneId, use24: Boolean) = AsNeededUi(
        medId = medication.id, name = medication.name, strength = medication.strength, dose = medication.doseText,
        summary = if (takenToday == 0) "Not taken today" else
            "Taken $takenToday× today" + (lastTakenAt?.let { " · last at ${TimeText.clock(ZoneTime.toLocal(it, zone), use24)}" } ?: ""),
        colorIndex = medication.colorIndex,
    )
}
