package com.dosecircle.app.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecircle.app.AppContainer
import com.dosecircle.core.PlanTier
import com.dosecircle.core.RolePolicy
import com.dosecircle.core.ScheduleExpander
import com.dosecircle.core.ScheduleKind
import com.dosecircle.core.SupplyCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class MedRowUi(val id: String, val title: String, val schedule: String, val supply: String?, val colorIndex: Int, val critical: Boolean)

data class MedicinesUi(
    val loading: Boolean = true,
    val person: String = "",
    val active: List<MedRowUi> = emptyList(),
    val asNeeded: List<MedRowUi> = emptyList(),
    val stopped: List<MedRowUi> = emptyList(),
    val canEdit: Boolean = false,
)

class MedicinesViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<MedicinesUi> = c.changes
        .onStart { emit(Unit) }
        .map { build() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicinesUi())

    private suspend fun build(): MedicinesUi = c.io {
        val settings = c.store.settings()
        val rec = c.store.recipients().let { all -> all.firstOrNull { it.id == settings.activeRecipientId } ?: all.firstOrNull() }
        val me = c.store.members().firstOrNull { it.id == settings.thisDeviceMemberId }
        if (rec == null || me == null) return@io MedicinesUi(loading = false)
        val pro = settings.effectiveTier(java.time.Instant.now()) == PlanTier.PRO
        val meds = c.store.medications(rec.id, includeArchived = true)

        fun row(m: com.dosecircle.core.Medication): MedRowUi {
            val days = if (pro) SupplyCalculator.daysRemaining(m) else null
            val supply = when {
                days == null -> null
                SupplyCalculator.needsRefill(m) -> if (days <= 0) "Out of stock" else "Refill soon · about $days ${if (days == 1) "day" else "days"} left"
                else -> "About $days days left"
            }
            return MedRowUi(m.id, m.displayName, ScheduleExpander.describe(m, settings.use24Hour), supply, m.colorIndex, m.priority == com.dosecircle.core.DosePriority.CRITICAL)
        }
        MedicinesUi(
            loading = false, person = rec.name,
            active = meds.filter { !it.isArchived && it.kind != ScheduleKind.AS_NEEDED }.map(::row),
            asNeeded = meds.filter { !it.isArchived && it.kind == ScheduleKind.AS_NEEDED }.map(::row),
            stopped = meds.filter { it.isArchived }.map(::row),
            canEdit = RolePolicy.canEditMedications(me.role),
        )
    }
}
