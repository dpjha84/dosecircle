@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dosecircle.app.ui.medicines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.ChoiceChips
import com.dosecircle.app.ui.components.IconAction
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.OutlineButton
import com.dosecircle.app.ui.components.PrimaryButton
import com.dosecircle.app.ui.components.ProBadge
import com.dosecircle.app.ui.components.SectionTitle
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.components.TimePickerDialog
import com.dosecircle.app.ui.components.TonalButton
import com.dosecircle.core.MedForm
import com.dosecircle.core.ScheduleKind
import com.dosecircle.core.TimeText

@Composable
fun MedicineEditScreen(medId: String?, onDone: () -> Unit, onPaywall: (String) -> Unit) {
    val vm: MedicineEditViewModel = appViewModel { MedicineEditViewModel(it, medId) }
    val f by vm.form.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    LaunchedEffect(result) { if (result != null) onDone() }

    var timeEditing by remember { mutableStateOf<Int?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(if (f.isNew) "Add medicine" else "Edit medicine", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onDone) },
                actions = { TextButton(onClick = vm::save, enabled = !f.loading) { Text("Save", style = MaterialTheme.typography.titleMedium) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Medicine name", f.name, { v -> vm.update { it.copy(name = v) } }, error = f.errors["name"], placeholder = "e.g. Metformin")
            Field("Strength (optional)", f.strength, { v -> vm.update { it.copy(strength = v) } }, placeholder = "e.g. 500 mg")

            SectionTitle("Type")
            ChoiceChips(
                MedForm.values().map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, setOf(f.form), onToggle = { vm.setForm(it) },
            )
            Field("How much each time?", f.doseText, { v -> vm.update { it.copy(doseText = v) } }, placeholder = "e.g. 1 tablet")
            Field("Instructions (optional)", f.instructions, { v -> vm.update { it.copy(instructions = v) } }, placeholder = "e.g. With food")
            ChoiceChips(
                listOf("With food", "Before bed", "On an empty stomach", "With water").map { it to it }, setOf(f.instructions),
                onToggle = { t -> vm.update { it.copy(instructions = if (it.instructions == t) "" else t) } },
            )

            SectionTitle("When")
            ChoiceChips(
                listOf(ScheduleKind.DAILY to "Every day", ScheduleKind.DAYS_OF_WEEK to "Some days", ScheduleKind.EVERY_N_DAYS to "Every few days", ScheduleKind.AS_NEEDED to "Only when needed"),
                setOf(f.kind), onToggle = { k -> vm.update { it.copy(kind = k) } },
            )
            if (f.kind == ScheduleKind.DAYS_OF_WEEK) {
                ChoiceChips(
                    listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 0 to "Sun"), f.days, onToggle = vm::toggleDay,
                )
                f.errors["days"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            if (f.kind == ScheduleKind.EVERY_N_DAYS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAction(Icons.Rounded.Remove, "Fewer days", { vm.update { it.copy(intervalDays = maxOf(2, it.intervalDays - 1)) } })
                    Text("Every ${f.intervalDays} days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    IconAction(Icons.Rounded.Add, "More days", { vm.update { it.copy(intervalDays = minOf(60, it.intervalDays + 1)) } })
                }
            }
            if (f.kind == ScheduleKind.AS_NEEDED) {
                Muted("You'll log a dose yourself whenever you take it. DoseCircle warns you if it's too soon after the last one.")
            } else {
                f.times.forEachIndexed { i, minutes ->
                    SurfaceCard {
                        Row(Modifier.clickable { timeEditing = i }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                            Text(TimeText.clock(minutes, f.use24Hour), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 14.dp))
                            if (f.times.size > 1) IconAction(Icons.Rounded.Close, "Remove this time", { vm.removeTime(i) })
                        }
                    }
                }
                OutlineButton("Add another time", vm::addTime, icon = Icons.Rounded.Add)
            }

            if (f.isNew && f.kind != ScheduleKind.AS_NEEDED) {
                SectionTitle("Start")
                ChoiceChips(listOf(false to "Today", true to "Tomorrow"), setOf(f.startTomorrow), onToggle = { v -> vm.update { it.copy(startTomorrow = v) } })
            }

            SectionTitle("Important medicine")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alert caregivers sooner", style = MaterialTheme.typography.bodyLarge)
                    Muted("Missed-dose alerts go out twice as fast and ignore quiet hours.")
                }
                Switch(f.critical, { v -> vm.update { it.copy(critical = v) } })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Supply and refills", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Spacer(Modifier.padding(start = 8.dp)); if (!f.isPro) ProBadge()
            }
            if (f.isPro && f.kind != ScheduleKind.AS_NEEDED) {
                Field("Doses or pills left", f.unitsRemaining, { v -> vm.update { it.copy(unitsRemaining = v) } }, error = f.errors["supply"], number = true, placeholder = "e.g. 30")
                Field("Used each time", f.unitsPerDose, { v -> vm.update { it.copy(unitsPerDose = v) } }, error = f.errors["perDose"], number = true)
                Field("Remind me when this many days are left", f.refillDays, { v -> vm.update { it.copy(refillDays = v) } }, number = true)
            } else if (!f.isPro) {
                SurfaceCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Know before a medicine runs out", style = MaterialTheme.typography.titleMedium)
                        Muted("Pro counts down your supply as doses are logged and reminds you to refill.")
                        TonalButton("See Pro", { onPaywall("Refill tracking tells you before a medicine runs out.") })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton(if (f.isNew) "Add medicine" else "Save changes", vm::save, enabled = !f.loading)
            if (!f.isNew) {
                if (f.isArchived) TonalButton("Start this medicine again", vm::restart)
                else OutlineButton("Stop this medicine", { confirmStop = true })
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete permanently", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    timeEditing?.let { i ->
        TimePickerDialog(f.times.getOrElse(i) { 8 * 60 }, f.use24Hour, onConfirm = { vm.setTime(i, it); timeEditing = null }, onDismiss = { timeEditing = null })
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop this medicine?") },
            text = { Text("Reminders stop. Its history stays so reports remain complete.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton({ confirmStop = false; vm.stop() }) { Text("Stop it", style = MaterialTheme.typography.labelLarge) } },
            dismissButton = { TextButton({ confirmStop = false }) { Text("Keep it", style = MaterialTheme.typography.labelLarge) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete permanently?") },
            text = { Text("This also erases its dose history. Use “Stop” instead if the medicine is just finished.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteForever() }) { Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } },
        )
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, error: String? = null, number: Boolean = false, placeholder: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } }, isError = error != null,
        supportingText = error?.let { { Text(it) } }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge, shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}
