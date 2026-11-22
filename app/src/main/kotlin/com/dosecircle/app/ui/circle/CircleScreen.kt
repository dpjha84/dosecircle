@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dosecircle.app.ui.circle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.Avatar
import com.dosecircle.app.ui.components.Banner
import com.dosecircle.app.ui.components.ChoiceChips
import com.dosecircle.app.ui.components.IconAction
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.PrimaryButton
import com.dosecircle.app.ui.components.SectionTitle
import com.dosecircle.app.ui.components.StatusPill
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.components.TimePickerDialog
import com.dosecircle.app.ui.components.TonalButton
import com.dosecircle.core.CircleRole
import com.dosecircle.core.RolePolicy
import com.dosecircle.core.TimeText

private val AssignableRoles = listOf(CircleRole.PRIMARY_CAREGIVER, CircleRole.CAREGIVER, CircleRole.AIDE, CircleRole.VIEWER, CircleRole.PATIENT)

@Composable
fun CircleScreen(onPaywall: (String) -> Unit, vm: CircleViewModel = appViewModel { CircleViewModel(it) }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<PersonUi?>(null) }
    var quietPicker by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) { vm.paywall.collect { onPaywall(it) } }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Care circle", style = MaterialTheme.typography.headlineMedium)
                if (ui.recipientName.isNotEmpty()) Muted("Everyone helping look after ${ui.recipientName}", style = MaterialTheme.typography.bodyLarge)
            }
            item { Banner("Everyone in the circle can log doses on this phone and pick their name under “Logging as” on the Today screen. Each person's name is saved with every dose.") }
            if (!ui.isPro) {
                item {
                    SurfaceCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Free includes you and one family member who can view.", style = MaterialTheme.typography.titleMedium)
                            Muted("Pro lets family and aides log doses with their names, and alerts a whole chain of caregivers.")
                            TonalButton("See Pro", { onPaywall("Pro adds your whole care circle.") })
                        }
                    }
                }
            }
            items(ui.people, key = { it.id }) { p -> PersonCard(p, onClick = { if (ui.canManage) managing = p }) }
            if (ui.canManage) item { PrimaryButton("Add a person", { adding = true }, icon = Icons.Rounded.PersonAdd) }

            item { SectionTitle("If a dose is missed") }
            item { Muted("Caregivers are alerted in this order until the dose is logged.", style = MaterialTheme.typography.bodyLarge) }
            if (ui.chain.isEmpty()) item { Muted("Nobody is set to be alerted yet.") }
            items(ui.chain.withIndex().toList(), key = { "chain-" + it.value.id }) { (i, p) ->
                SurfaceCard {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp, start = 4.dp))
                        Avatar(p.name, p.colorIndex, 36.dp)
                        Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (ui.canManage) {
                            if (i > 0) IconAction(Icons.Rounded.KeyboardArrowUp, "Move up", { vm.moveUp(p.id) })
                            if (i < ui.chain.size - 1) IconAction(Icons.Rounded.KeyboardArrowDown, "Move down", { vm.moveDown(p.id) })
                            IconAction(Icons.Rounded.Close, "Remove from alerts", { vm.removeFromChain(p.id) })
                        }
                    }
                }
            }
            if (ui.canManage && ui.addableToChain.isNotEmpty()) {
                item {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(onClick = { open = true }, label = { Text("Add someone to alerts", style = MaterialTheme.typography.bodyLarge) }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                        DropdownMenu(open, { open = false }) {
                            ui.addableToChain.forEach { p -> DropdownMenuItem(text = { Text(p.name, style = MaterialTheme.typography.bodyLarge) }, onClick = { open = false; vm.addToChain(p.id) }) }
                        }
                    }
                }
            }
            if (ui.canManage) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("First alert after", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        ChoiceChips(listOf(15, 30, 45, 60).map { it to "$it min" }, setOf(ui.firstAlertAfter), onToggle = vm::setFirstAlert)
                        Text("Then the next person after another", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        ChoiceChips(listOf(10, 15, 30).map { it to "$it min" }, setOf(ui.stepGap), onToggle = vm::setStepGap)
                    }
                }
            }

            item { SectionTitle("Quiet hours") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hold routine alerts overnight", style = MaterialTheme.typography.bodyLarge)
                        Muted("Important medicines always alert straight away.")
                    }
                    Switch(ui.quietEnabled, { vm.setQuiet(enabled = it) })
                }
            }
            if (ui.quietEnabled) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TonalButton("From ${TimeText.clock(ui.quietStart, ui.use24Hour)}", { quietPicker = "start" }, Modifier.weight(1f))
                        TonalButton("Until ${TimeText.clock(ui.quietEnd, ui.use24Hour)}", { quietPicker = "end" }, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (adding) AddPersonDialog(onDismiss = { adding = false }, onAdd = { n, r -> adding = false; vm.addPerson(n, r) })
    managing?.let { p ->
        ManageDialog(p, onDismiss = { managing = null }, vm = vm)
    }
    quietPicker?.let { which ->
        TimePickerDialog(if (which == "start") ui.quietStart else ui.quietEnd, ui.use24Hour,
            onConfirm = { m -> if (which == "start") vm.setQuiet(start = m) else vm.setQuiet(end = m); quietPicker = null },
            onDismiss = { quietPicker = null })
    }
}

@Composable
private fun PersonCard(p: PersonUi, onClick: () -> Unit) {
    SurfaceCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(p.name, p.colorIndex, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    if (p.isThisDevice) StatusPill("This phone", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Muted(p.roleLabel)
                val bits = listOfNotNull(if (p.reminders) "Dose reminders" else null, if (p.missedAlerts) "Missed-dose alerts" else null)
                if (bits.isNotEmpty()) Muted(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String, CircleRole) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(CircleRole.CAREGIVER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                Text("Their role", style = MaterialTheme.typography.titleSmall)
                ChoiceChips(AssignableRoles.map { it to RolePolicy.label(it) }, setOf(role), onToggle = { role = it })
            }
        },
        confirmButton = { TextButton({ onAdd(name, role) }, enabled = name.isNotBlank()) { Text("Add", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } },
    )
}

@Composable
private fun ManageDialog(p: PersonUi, onDismiss: () -> Unit, vm: CircleViewModel) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Role", style = MaterialTheme.typography.titleSmall)
                ChoiceChips(AssignableRoles.map { it to RolePolicy.label(it) }, setOf(p.role), onToggle = { vm.changeRole(p.id, it); onDismiss() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dose reminders", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(p.reminders, { vm.setReminders(p.id, it); onDismiss() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Missed-dose alerts", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(p.missedAlerts, { vm.setMissedAlerts(p.id, it); onDismiss() })
                }
                if (!p.isThisDevice) {
                    TextButton({ vm.remove(p.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Remove from circle", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", style = MaterialTheme.typography.labelLarge) } },
    )
}
