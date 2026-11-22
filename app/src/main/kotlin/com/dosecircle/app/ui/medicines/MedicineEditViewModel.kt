package com.dosecircle.app.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.core.DosePriority
import com.dosecircle.core.MedForm
import com.dosecircle.core.Medication
import com.dosecircle.core.PlanTier
import com.dosecircle.core.ScheduleKind
import com.dosecircle.core.ZoneTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class MedFormState(
    val loading: Boolean = true,
    val id: String? = null,
    val name: String = "",
    val strength: String = "",
    val form: MedForm = MedForm.TABLET,
    val doseText: String = "1 tablet",
    val instructions: String = "",
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val times: List<Int> = listOf(8 * 60),
    val days: Set<Int> = (0..6).toSet(),
    val intervalDays: Int = 2,
    val critical: Boolean = false,
    val startTomorrow: Boolean = false,
    val unitsRemaining: String = "",
    val unitsPerDose: String = "1",
    val refillDays: String = "7",
    val isArchived: Boolean = false,
    val isPro: Boolean = false,
    val use24Hour: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
) {
    val isNew: Boolean get() = id == null
}

sealed interface EditResult {
    data object Saved : EditResult
    data object Removed : EditResult
}

class MedicineEditViewModel(private val c: AppContainer, private val medId: String?) : ViewModel() {
    private val _form = MutableStateFlow(MedFormState())
    val form: StateFlow<MedFormState> = _form.asStateFlow()

    private val _result = MutableStateFlow<EditResult?>(null)
    val result: StateFlow<EditResult?> = _result.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = c.io {
                val s = c.store.settings()
                val pro = s.effectiveTier(Instant.now()) == PlanTier.PRO
                val existing = medId?.let { c.store.medication(it) }
                if (existing == null) {
                    val rec = c.store.recipients().let { all -> all.firstOrNull { it.id == s.activeRecipientId } ?: all.firstOrNull() }
                    val local = ZoneTime.toLocal(Instant.now(), ZoneTime.resolve(rec?.timeZoneId))
                    // If the first dose time has already passed today, offer to start tomorrow so it doesn't show up as missed.
                    MedFormState(loading = false, isPro = pro, use24Hour = s.use24Hour, startTomorrow = local.hour * 60 + local.minute >= 8 * 60)
                } else {
                    MedFormState(
                        loading = false, id = existing.id, name = existing.name, strength = existing.strength, form = existing.form,
                        doseText = existing.doseText, instructions = existing.instructions, kind = existing.kind,
                        times = existing.timesOfDayMinutes.sorted().ifEmpty { listOf(8 * 60) },
                        days = (0..6).filter { ((existing.daysOfWeekMask shr it) and 1) == 1 }.toSet(),
                        intervalDays = maxOf(2, existing.intervalDays), critical = existing.priority == DosePriority.CRITICAL,
                        unitsRemaining = existing.unitsRemaining?.let { fmt(it) } ?: "", unitsPerDose = fmt(existing.unitsPerDose),
                        refillDays = existing.refillThresholdDays.toString(), isArchived = existing.isArchived, isPro = pro, use24Hour = s.use24Hour,
                    )
                }
            }
            _form.value = loaded
        }
    }

    fun update(change: (MedFormState) -> MedFormState) = _form.update { change(it).copy(errors = emptyMap()) }

    fun setForm(form: MedForm) = update {
        val previousDefault = defaultDose(it.form)
        it.copy(form = form, doseText = if (it.doseText == previousDefault || it.doseText.isBlank()) defaultDose(form) else it.doseText)
    }

    fun toggleDay(day: Int) = update { it.copy(days = if (day in it.days) it.days - day else it.days + day) }

    fun setTime(index: Int, minutes: Int) = update { f -> f.copy(times = f.times.toMutableList().also { it[index] = minutes }) }
    fun removeTime(index: Int) = update { f -> if (f.times.size <= 1) f else f.copy(times = f.times.toMutableList().also { it.removeAt(index) }) }
    fun addTime() = update { f -> f.copy(times = f.times + minOf(23 * 60, (f.times.maxOrNull() ?: (8 * 60 - 240)) + 240)) }

    fun save() {
        viewModelScope.launch {
            val f = _form.value
            val errors = validate(f)
            if (errors.isNotEmpty()) { _form.value = f.copy(errors = errors); return@launch }
            c.io {
                val s = c.store.settings()
                val pro = s.effectiveTier(Instant.now()) == PlanTier.PRO
                val rec = c.store.recipients().let { all -> all.firstOrNull { it.id == s.activeRecipientId } ?: all.first() }
                val existing = f.id?.let { c.store.medication(it) }
                val today = ZoneTime.today(Instant.now(), ZoneTime.resolve(rec.timeZoneId))
                val base = existing ?: Medication(recipientId = rec.id, colorIndex = c.store.medications(rec.id, true).size % 8)
                val mask = f.days.fold(0) { acc, d -> acc or (1 shl d) }
                val med = base.copy(
                    name = f.name.trim(), strength = f.strength.trim(), form = f.form, doseText = f.doseText.trim().ifEmpty { defaultDose(f.form) },
                    instructions = f.instructions.trim(), kind = f.kind,
                    timesOfDayMinutes = if (f.kind == ScheduleKind.AS_NEEDED) emptyList() else f.times.distinct().sorted(),
                    daysOfWeekMask = if (f.kind == ScheduleKind.DAYS_OF_WEEK) mask else 127,
                    intervalDays = if (f.kind == ScheduleKind.EVERY_N_DAYS) f.intervalDays else 1,
                    startDate = existing?.startDate ?: if (f.startTomorrow) today.plusDays(1) else today,
                    priority = if (f.critical) DosePriority.CRITICAL else DosePriority.NORMAL,
                    // Supply tracking is a Pro feature: Free keeps whatever was there and never edits it.
                    unitsRemaining = if (pro) number(f.unitsRemaining) else base.unitsRemaining,
                    unitsPerDose = if (pro) (number(f.unitsPerDose) ?: 1.0).takeIf { it > 0 } ?: 1.0 else base.unitsPerDose,
                    refillThresholdDays = if (pro) f.refillDays.trim().toIntOrNull()?.coerceIn(1, 60) ?: 7 else base.refillThresholdDays,
                )
                c.store.saveMedication(med)
            }
            c.afterDataChange()
            _result.value = EditResult.Saved
        }
    }

    fun stop() = change { med, today -> c.store.saveMedication(med.copy(isArchived = true, endDate = today.minusDays(1))) }
    fun restart() = change { med, today -> c.store.saveMedication(med.copy(isArchived = false, endDate = null, startDate = today)) }
    fun deleteForever() = change { med, _ -> c.store.deleteMedication(med.id) }

    private fun change(action: (Medication, java.time.LocalDate) -> Unit) {
        val id = medId ?: return
        viewModelScope.launch {
            c.io {
                val med = c.store.medication(id) ?: return@io
                val s = c.store.settings()
                val rec = c.store.recipients().firstOrNull { it.id == med.recipientId }
                action(med, ZoneTime.today(Instant.now(), ZoneTime.resolve(rec?.timeZoneId)))
            }
            c.afterDataChange()
            _result.value = EditResult.Removed
        }
    }

    private fun validate(f: MedFormState): Map<String, String> {
        val e = HashMap<String, String>()
        if (f.name.isBlank()) e["name"] = "Enter the medicine's name."
        if (f.kind == ScheduleKind.DAYS_OF_WEEK && f.days.isEmpty()) e["days"] = "Choose at least one day."
        if (f.kind != ScheduleKind.AS_NEEDED && f.times.isEmpty()) e["times"] = "Add at least one time."
        if (f.isPro) {
            if (f.unitsRemaining.isNotBlank() && number(f.unitsRemaining).let { it == null || it < 0 }) e["supply"] = "Enter a number, like 30."
            if (f.unitsPerDose.isNotBlank() && number(f.unitsPerDose).let { it == null || it <= 0 }) e["perDose"] = "Enter a number above zero, like 1 or 0.5."
        }
        return e
    }

    private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    companion object {
        fun defaultDose(form: MedForm) = when (form) {
            MedForm.TABLET -> "1 tablet"
            MedForm.CAPSULE -> "1 capsule"
            MedForm.LIQUID -> "1 measure"
            MedForm.DROPS -> "2 drops"
            MedForm.INJECTION -> "1 injection"
            MedForm.INHALER -> "2 puffs"
            MedForm.PATCH -> "1 patch"
            MedForm.CREAM -> "Apply thinly"
            MedForm.OTHER -> "1 dose"
        }
    }
}
