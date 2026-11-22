@file:OptIn(ExperimentalMaterial3Api::class)

package com.dosecircle.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.BuildConfig
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.ChoiceChips
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.OutlineButton
import com.dosecircle.app.ui.components.PrimaryButton
import com.dosecircle.app.ui.components.SectionTitle
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.components.TonalButton
import com.dosecircle.core.ReminderStyle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onPaywall: (String) -> Unit, vm: SettingsViewModel = appViewModel { SettingsViewModel(it) }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val s = ui.settings
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmErase by remember { mutableStateOf(false) }

    // Permissions can change while the user is in system settings, so re-check every time we come back.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshHealth() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("More", style = MaterialTheme.typography.headlineMedium)

            SectionTitle("Your plan")
            SurfaceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = MaterialTheme.colorScheme.secondary)
                        Text(if (ui.isPro) "DoseCircle Pro" else "Free plan", style = MaterialTheme.typography.titleLarge)
                    }
                    if (ui.isPro) {
                        Muted("Thank you. Your whole care circle, refill tracking and doctor reports are unlocked.")
                        OutlineButton("Manage subscription", { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) })
                    } else {
                        Muted("Reminders, the full history and unlimited medicines are free. Pro adds your care circle, refill tracking and doctor reports.")
                        PrimaryButton("See Pro", { onPaywall("") })
                    }
                    TextButton(onClick = { vm.refreshEntitlement { active -> scope.launch { snackbar.showSnackbar(if (active) "Pro restored." else "No active subscription found.") } } }) {
                        Text("Restore purchases", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            SectionTitle("Reminders")
            Text("How should reminders behave?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(
                listOf(ReminderStyle.PERSISTENT to "Keep reminding me", ReminderStyle.GENTLE to "Remind me gently"),
                setOf(s.reminders.style), onToggle = vm::setStyle,
            )
            Muted(if (s.reminders.style == ReminderStyle.PERSISTENT) "A reminder at the time, then every ${s.reminders.nagIntervalMinutes} minutes until you log the dose." else "A reminder at the time and one follow-up 15 minutes later.")
            LabeledChips("Heads-up before", listOf(0 to "None", 5 to "5 min", 10 to "10 min", 15 to "15 min"), s.reminders.headsUpMinutes, vm::setHeadsUp)
            LabeledChips("Snooze for", listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"), s.reminders.snoozeMinutes, vm::setSnooze)
            LabeledChips("Call a dose “missed” after", listOf(30 to "30 min", 60 to "1 hour", 90 to "90 min", 120 to "2 hours"), s.reminders.missedAfterMinutes, vm::setMissedAfter)

            SectionTitle("Reminders reaching you")
            health?.let { h -> ReminderHealthCard(h, vm.c.scheduler, onChanged = vm::refreshHealth, onSendTest = { vm.sendTest(); scope.launch { snackbar.showSnackbar("A test reminder will arrive in a few seconds.") } }) }

            SectionTitle("Display")
            ToggleRow("Comfort mode", "Larger text and bigger buttons.", s.comfortMode, vm::setComfort)
            ToggleRow("24-hour clock", "Show 18:00 instead of 6:00 PM.", s.use24Hour, vm::set24Hour)

            SectionTitle("Privacy")
            Muted("Everything you enter stays on this phone. DoseCircle has no account, no ads and no analytics, and it is excluded from Android cloud backup.", style = MaterialTheme.typography.bodyLarge)
            OutlineButton("Erase everything on this phone", { confirmErase = true }, Modifier.padding(top = 10.dp))

            SectionTitle("About")
            Muted("DoseCircle ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Muted("DoseCircle helps you remember and record medicines. It is not a medical device and does not give medical advice. Always follow your doctor's or pharmacist's instructions, and call a professional if you are unsure about a dose.", Modifier.padding(top = 6.dp))

            if (BuildConfig.DEBUG) {
                SectionTitle("Debug build")
                TonalButton(if (ui.isPro) "Debug: switch to Free" else "Debug: unlock Pro", vm::debugTogglePro)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = { Text("This deletes every medicine, dose record and person on this phone. It can't be undone.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton({ confirmErase = false; vm.eraseEverything() }) { Text("Erase", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) } },
            dismissButton = { TextButton({ confirmErase = false }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } },
        )
    }
}

@Composable
private fun LabeledChips(label: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChips(options, setOf(selected), onToggle = onSelect)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Muted(subtitle)
        }
        Switch(checked, onChange)
    }
}
