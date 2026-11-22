package com.dosecircle.app.ui.history

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.core.AdherenceCalculator
import com.dosecircle.core.DoctorReport
import com.dosecircle.core.Entitlements
import com.dosecircle.core.Feature
import com.dosecircle.core.HistoryBuilder
import com.dosecircle.core.HistoryOutcome
import com.dosecircle.core.HistoryRow
import com.dosecircle.core.TimeText
import com.dosecircle.core.ZoneTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

enum class DayDot { NONE, ALL_TAKEN, PARTIAL, ALL_MISSED }

data class DayDotUi(val label: String, val dot: DayDot)
data class HistoryRowUi(val key: String, val time: String, val title: String, val outcome: HistoryOutcome, val outcomeText: String, val detail: String)
data class DayGroupUi(val title: String, val rows: List<HistoryRowUi>)

data class HistoryUi(
    val loading: Boolean = true,
    val days: Int = 30,
    val person: String = "",
    val percentText: String = "–",
    val fraction: Float = 0f,
    val summary: String = "",
    val strip: List<DayDotUi> = emptyList(),
    val groups: List<DayGroupUi> = emptyList(),
    val isPro: Boolean = false,
)

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(30)

    val ui: StateFlow<HistoryUi> = combine(period, c.changes.onStart { emit(Unit) }) { p, _ -> p }
        .map { build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    private val _share = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val share: SharedFlow<Intent> = _share.asSharedFlow()
    private val _paywall = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val paywall: SharedFlow<String> = _paywall.asSharedFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun setPeriod(days: Int) { period.value = days }

    private class Snapshot(
        val rec: com.dosecircle.core.CareRecipient, val meds: List<com.dosecircle.core.Medication>, val rows: List<HistoryRow>,
        val from: LocalDate, val to: LocalDate, val use24: Boolean, val pro: Boolean,
    )

    private fun snapshot(days: Int): Snapshot? {
        val s = c.store.settings()
        val rec = c.store.recipients().let { all -> all.firstOrNull { it.id == s.activeRecipientId } ?: all.firstOrNull() } ?: return null
        val now = Instant.now()
        val today = ZoneTime.today(now, ZoneTime.resolve(rec.timeZoneId))
        val from = today.minusDays(days - 1L)
        val meds = c.store.medications(rec.id, includeArchived = true)
        val rows = HistoryBuilder.build(rec, meds, c.store.entries(rec.id, from, today), c.store.members(), from, today, now, s.reminders)
        return Snapshot(rec, meds, rows, from, today, s.use24Hour, Entitlements.has(s.effectiveTier(now), Feature.DOCTOR_REPORT))
    }

    fun exportCsv() {
        viewModelScope.launch {
            val intent = c.io { snapshot(period.value)?.let { c.exporter.csv(it.rec.name, it.rows, it.from, it.to) } }
            if (intent != null) _share.emit(intent) else _message.emit("Nothing to export yet.")
        }
    }

    fun exportReport() {
        viewModelScope.launch {
            val result = c.io {
                val snap = snapshot(period.value) ?: return@io null
                if (!snap.pro) return@io "paywall"
                c.exporter.pdf(DoctorReport.build(snap.rec, snap.meds, snap.rows, snap.from, snap.to, snap.use24), snap.use24)
            }
            when (result) {
                null -> _message.emit("Nothing to export yet.")
                is String -> _paywall.emit(Entitlements.describe(Feature.DOCTOR_REPORT))
                is Intent -> _share.emit(result)
            }
        }
    }

    private suspend fun build(days: Int): HistoryUi = c.io {
        val snap = snapshot(days) ?: return@io HistoryUi(loading = false, days = days)
        val report = AdherenceCalculator.compute(snap.rows, snap.from, snap.to)
        val s = c.store.settings()
        val zone = ZoneTime.resolve(snap.rec.timeZoneId)

        val strip = report.days.takeLast(minOf(days, 14)).map { d ->
            DayDotUi(
                d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.ENGLISH),
                when {
                    d.total == 0 -> DayDot.NONE
                    d.missed == 0 && d.skipped == 0 -> DayDot.ALL_TAKEN
                    d.taken == 0 -> DayDot.ALL_MISSED
                    else -> DayDot.PARTIAL
                },
            )
        }
        val today = snap.to
        val groups = snap.rows.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { (date, rows) ->
            DayGroupUi(
                TimeText.dayLabel(date, today),
                rows.sortedByDescending { it.dueLocal }.mapIndexed { i, r ->
                    HistoryRowUi(
                        key = "$date-${r.medicationId}-${r.dueLocal}-$i",
                        time = TimeText.clock(r.dueLocal, s.use24Hour),
                        title = listOf(r.medicationName, r.strength).filter { it.isNotBlank() }.joinToString(" "),
                        outcome = r.outcome, outcomeText = r.outcomeText,
                        detail = listOfNotNull(
                            r.loggedBy.takeIf { it.isNotBlank() && r.outcome != HistoryOutcome.MISSED }?.let { who ->
                                "by $who" + (r.loggedAtLocal?.let { " at ${TimeText.clock(it, s.use24Hour)}" } ?: "")
                            },
                            r.note.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                    )
                },
            )
        }
        HistoryUi(
            loading = false, days = days, person = snap.rec.name,
            percentText = if (report.scheduled == 0) "–" else "${Math.round(report.percent)}%",
            fraction = if (report.scheduled == 0) 0f else (report.taken.toFloat() / report.scheduled),
            summary = if (report.scheduled == 0) "No doses to count yet." else
                "${report.taken} of ${report.scheduled} doses taken" + (if (report.missed > 0) " · ${report.missed} missed" else "") + (if (report.skipped > 0) " · ${report.skipped} skipped" else ""),
            strip = strip, groups = groups, isPro = snap.pro,
        )
    }
}
