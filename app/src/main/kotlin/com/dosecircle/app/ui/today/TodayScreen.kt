@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dosecircle.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.Avatar
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.PrimaryButton
import com.dosecircle.app.ui.components.StatusPill
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.components.TonalButton
import com.dosecircle.app.ui.theme.MedPalette
import com.dosecircle.core.DoseStatus
import com.dosecircle.core.SlotState
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(onAddMedicine: () -> Unit, vm: TodayViewModel = appViewModel { TodayViewModel(it) }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val confirm by vm.confirm.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var scrolled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                when (e) {
                    is TodayEvent.Logged -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val r = snackbar.showSnackbar(e.message, actionLabel = "Undo", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) vm.undo(e.entryIds)
                    }
                    is TodayEvent.Message -> snackbar.showSnackbar(e.text)
                }
            }
        }
    }
    // Land on the next dose that still needs doing, once.
    LaunchedEffect(ui.loading, ui.nextIndex) {
        if (!scrolled && !ui.loading) {
            if (ui.nextIndex > 0) listState.scrollToItem(ui.nextIndex + 1)
            scrolled = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { Header(ui, vm) }
            if (!ui.loading && ui.slots.isEmpty() && ui.asNeeded.isEmpty()) {
                item(key = "empty") { EmptyState(ui.hasMedicines, ui.canEditMedicines, onAddMedicine) }
            }
            items(ui.slots, key = { it.key }) { slot ->
                SlotCard(
                    slot,
                    onTakeAll = { vm.take(slot.items.filter { it.status != DoseStatus.TAKEN && it.status != DoseStatus.SKIPPED }) },
                    onSkipAll = { vm.skip(slot.items.filter { it.status != DoseStatus.TAKEN && it.status != DoseStatus.SKIPPED }) },
                    onTakeOne = { vm.take(listOf(it)) },
                    onSkipOne = { vm.skip(listOf(it)) },
                )
            }
            if (ui.asNeeded.isNotEmpty()) {
                item(key = "prn-title") { Text("When needed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
                items(ui.asNeeded, key = { "prn-" + it.medId }) { AsNeededCard(it) { vm.logAsNeeded(it) } }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = vm::dismissConfirm,
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Check before logging") },
            text = { Text(c.message + "\n\nLog it anyway?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = vm::confirmOverride) { Text("Log anyway", style = MaterialTheme.typography.labelLarge) } },
            dismissButton = { TextButton(onClick = vm::dismissConfirm) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } },
        )
    }
}

@Composable
private fun Header(ui: TodayUi, vm: TodayViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(ui.greeting, style = MaterialTheme.typography.headlineMedium)
            Muted(ui.subtitle, style = MaterialTheme.typography.bodyLarge)
        }
        if (ui.recipients.size > 1) {
            var open by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { open = true },
                    label = { Text(ui.recipients.firstOrNull { it.id == ui.activeRecipientId }?.name ?: "Choose person", style = MaterialTheme.typography.bodyLarge) },
                    trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) },
                )
                DropdownMenu(open, { open = false }) {
                    ui.recipients.forEach { r -> DropdownMenuItem(text = { Text(r.name, style = MaterialTheme.typography.bodyLarge) }, onClick = { open = false; vm.selectRecipient(r.id) }) }
                }
            }
        }
        SurfaceCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.progressText.ifEmpty { " " }, style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { ui.progress }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (ui.allDone) Muted("Everything for today is taken care of.")
            }
        }
        if (ui.loggers.size > 1) {
            var open by remember { mutableStateOf(false) }
            val current = ui.loggers.firstOrNull { it.id == ui.loggerId }
            Box {
                AssistChip(
                    onClick = { open = true },
                    label = { Text("Logging as ${current?.name ?: "…"}", style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { current?.let { Avatar(it.name, it.colorIndex, 24.dp) } },
                    trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) },
                )
                DropdownMenu(open, { open = false }) {
                    ui.loggers.forEach { m -> DropdownMenuItem(text = { Text(m.name, style = MaterialTheme.typography.bodyLarge) }, onClick = { open = false; vm.selectLogger(m.id) }) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasMedicines: Boolean, canEdit: Boolean, onAdd: () -> Unit) {
    SurfaceCard {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MedicalServices, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(if (hasMedicines) "Nothing due today" else "No medicines yet", style = MaterialTheme.typography.titleLarge)
            Muted(if (hasMedicines) "Your next dose will show up here." else "Add a medicine and its times, and DoseCircle will remind you.")
            if (canEdit && !hasMedicines) PrimaryButton("Add a medicine", onAdd, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun SlotCard(slot: SlotUi, onTakeAll: () -> Unit, onSkipAll: () -> Unit, onTakeOne: (ItemUi) -> Unit, onSkipOne: (ItemUi) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent: Color
    val container: Color
    val pillContainer: Color
    val pillContent: Color
    when (slot.state) {
        SlotState.DUE -> { accent = cs.primary; container = cs.primaryContainer; pillContainer = cs.primary; pillContent = cs.onPrimary }
        SlotState.MISSED -> { accent = cs.error; container = cs.errorContainer; pillContainer = cs.error; pillContent = cs.onError }
        SlotState.UPCOMING -> { accent = cs.outline; container = cs.surfaceContainerLowest; pillContainer = cs.surfaceVariant; pillContent = cs.onSurfaceVariant }
        SlotState.DONE -> { accent = cs.outlineVariant; container = cs.surfaceContainerLow; pillContainer = cs.surfaceVariant; pillContent = cs.onSurfaceVariant }
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${slot.time}, ${slot.stateText}" },
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(10.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.time, style = MaterialTheme.typography.headlineMedium)
                        Muted(slot.period)
                    }
                    if (slot.state == SlotState.DONE) {
                        Icon(Icons.Rounded.CheckCircle, "Done", tint = cs.primary, modifier = Modifier.size(32.dp))
                    } else {
                        StatusPill(slot.stateText, pillContainer, pillContent)
                    }
                }
                if (slot.state == SlotState.DONE) {
                    Muted(slot.doneSummary, style = MaterialTheme.typography.bodyLarge)
                } else {
                    slot.items.forEach { ItemRow(it, onTakeOne = { onTakeOne(it) }) }
                    PrimaryButton(if (slot.pending == 1) "I took it" else "I took all ${slot.pending}", onTakeAll, icon = Icons.Rounded.Check)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = onSkipAll) { Text(if (slot.pending == 1) "Skip this dose" else "Skip these", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItemUi, onTakeOne: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pending = item.status != DoseStatus.TAKEN && item.status != DoseStatus.SKIPPED
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(MedPalette[Math.floorMod(item.colorIndex, MedPalette.size)]))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.strength.isNotBlank()) {
                    Surface(color = cs.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(item.strength, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Muted(listOf(item.dose, item.instructions).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            if (!pending) Muted(item.statusText, style = MaterialTheme.typography.bodySmall)
        }
        if (pending) {
            FilledTonalIconButton(onClick = onTakeOne, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Check, "Mark ${item.name} as taken") }
        } else {
            Icon(Icons.Rounded.CheckCircle, "Taken", tint = cs.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun AsNeededCard(item: AsNeededUi, onLog: () -> Unit) {
    SurfaceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(MedPalette[Math.floorMod(item.colorIndex, MedPalette.size)]))
                Column(Modifier.weight(1f)) {
                    Text(listOf(item.name, item.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Muted(item.summary)
                }
            }
            TonalButton("Log a dose", onLog, icon = Icons.Rounded.Add)
        }
    }
}
