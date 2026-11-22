@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dosecircle.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.ChoiceChips
import com.dosecircle.app.ui.components.IconAction
import com.dosecircle.app.ui.components.LinkButton
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.PrimaryButton
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.settings.ReminderHealthCard
import com.dosecircle.core.OnboardingRole
import com.dosecircle.core.ReminderStyle

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = appViewModel { OnboardingViewModel(it) }) {
    val s by vm.state.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshHealth() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Progress dots + back
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.step != Step.WELCOME && s.step != Step.DONE) IconAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", vm::back) else Spacer(Modifier.size(48.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    Step.values().forEach { st ->
                        Box(Modifier.padding(3.dp).size(if (st == s.step) 12.dp else 8.dp).clip(CircleShape)
                            .background(if (st.ordinal <= s.step.ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.size(48.dp))
            }

            Box(Modifier.weight(1f)) {
                AnimatedContent(s.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { step ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (step) {
                            Step.WELCOME -> Welcome(s, vm)
                            Step.ROLE -> RoleStep(s, vm)
                            Step.NAMES -> NamesStep(s, vm)
                            Step.MEDICINE -> MedicineStep(s, vm)
                            Step.REMINDERS -> RemindersStep(s, vm)
                            Step.PERMISSIONS -> PermissionsStep(health, vm)
                            Step.DONE -> DoneStep(s)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (s.step) {
                    Step.WELCOME -> PrimaryButton("Get started", vm::next)
                    Step.DONE -> PrimaryButton("Open DoseCircle", { vm.finish(onFinished) }, enabled = !s.saving)
                    else -> PrimaryButton("Continue", vm::next, enabled = s.canContinue)
                }
                if (s.step == Step.MEDICINE) LinkButton("Skip for now", { vm.edit { it.copy(medName = "") }; vm.next() }, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
    if (sub != null) Muted(sub, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ColumnScope.Welcome(s: OnboardingState, vm: OnboardingViewModel) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.align(Alignment.CenterHorizontally).size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MedicalServices, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
    Text("DoseCircle", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text("Never miss a dose. Never double up.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
    Muted("Simple reminders for medicines, and a shared record so everyone who helps knows what has been given.", Modifier.fillMaxWidth(), MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center))
    SurfaceCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Larger text", style = MaterialTheme.typography.titleMedium)
                Muted("Easier to read. You can change this later.")
            }
            Switch(s.comfort, vm::setComfort)
        }
    }
}

@Composable
private fun RoleStep(s: OnboardingState, vm: OnboardingViewModel) {
    Title("Who is this for?", "Pick the one that fits best. You can add more people later.")
    RoleCard(Icons.Rounded.Person, "I take medicine myself", "Reminders and a simple log for you.", s.role == OnboardingRole.SELF) { vm.edit { it.copy(role = OnboardingRole.SELF) } }
    RoleCard(Icons.Rounded.VolunteerActivism, "I look after someone", "A parent, partner or child. You'll be alerted if a dose is missed.", s.role == OnboardingRole.FAMILY_CAREGIVER) { vm.edit { it.copy(role = OnboardingRole.FAMILY_CAREGIVER) } }
    RoleCard(Icons.Rounded.Groups, "I'm a home aide or helper", "Log the doses you give so the family can see them.", s.role == OnboardingRole.AIDE) { vm.edit { it.copy(role = OnboardingRole.AIDE) } }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) cs.primaryContainer else cs.surfaceContainerLowest),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (selected) cs.primary else cs.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (selected) cs.onPrimary else cs.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Muted(detail)
            }
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = cs.primary)
        }
    }
}

@Composable
private fun NamesStep(s: OnboardingState, vm: OnboardingViewModel) {
    Title(if (s.isSelf) "What should we call you?" else "A couple of names")
    TextBox("Your name", s.yourName, { v -> vm.edit { it.copy(yourName = v) } }, ImeAction.Next)
    if (!s.isSelf) {
        TextBox("Who do you care for?", s.personName, { v -> vm.edit { it.copy(personName = v) } }, ImeAction.Done)
        Muted("Their first name is fine. It appears on reminders, like “Time for Raj's 8:00 AM medicines”.")
    }
}

@Composable
private fun MedicineStep(s: OnboardingState, vm: OnboardingViewModel) {
    Title("Add a first medicine", "You can add the rest in a minute. This is optional.")
    TextBox("Medicine name", s.medName, { v -> vm.edit { it.copy(medName = v) } }, ImeAction.Next)
    TextBox("Strength (optional)", s.strength, { v -> vm.edit { it.copy(strength = v) } }, ImeAction.Next, placeholder = "e.g. 500 mg")
    TextBox("How much each time?", s.dose, { v -> vm.edit { it.copy(dose = v) } }, ImeAction.Done)
    Text("When?", style = MaterialTheme.typography.titleMedium)
    ChoiceChips(TimePresets, s.times, onToggle = { t -> vm.edit { it.copy(times = if (t in it.times) it.times - t else it.times + t) } })
    Muted("You can set exact times and days in Medicines.")
}

@Composable
private fun RemindersStep(s: OnboardingState, vm: OnboardingViewModel) {
    Title("How should reminders work?")
    StyleCard("Keep reminding me", "A reminder at the time, then every 5 minutes until you log the dose. Best for avoiding missed doses.", s.style == ReminderStyle.PERSISTENT) { vm.edit { it.copy(style = ReminderStyle.PERSISTENT) } }
    StyleCard("Remind me gently", "One reminder at the time and one follow-up 15 minutes later.", s.style == ReminderStyle.GENTLE) { vm.edit { it.copy(style = ReminderStyle.GENTLE) } }
    Text("Heads-up before the dose", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
    ChoiceChips(listOf(0 to "None", 5 to "5 min", 10 to "10 min", 15 to "15 min"), setOf(s.headsUp), onToggle = { v -> vm.edit { it.copy(headsUp = v) } })
    when (s.role) {
        OnboardingRole.FAMILY_CAREGIVER -> {
            Text("If a dose isn't logged, alert me after", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            ChoiceChips(listOf(15, 30, 45, 60).map { it to "$it min" }, setOf(s.alertAfter), onToggle = { v -> vm.edit { it.copy(alertAfter = v) } })
        }
        OnboardingRole.SELF -> Muted("Later you can add a family member in Care circle so they're told if a dose is missed.")
        else -> Muted("The main caregiver can be added in Care circle to receive missed-dose alerts.")
    }
}

@Composable
private fun StyleCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) cs.primaryContainer else cs.surfaceContainerLowest),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(detail)
        }
    }
}

@Composable
private fun PermissionsStep(health: com.dosecircle.app.reminders.ReminderHealth?, vm: OnboardingViewModel) {
    Title("Let reminders reach you", "Android needs your permission to show reminders on time. This matters most, so please allow both.")
    health?.let { ReminderHealthCard(it, vm.scheduler, onChanged = vm::refreshHealth, onSendTest = vm::sendTest) }
}

@Composable
private fun ColumnScope.DoneStep(s: OnboardingState) {
    Spacer(Modifier.height(24.dp))
    Box(Modifier.align(Alignment.CenterHorizontally).size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
    }
    Text("You're all set", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    val who = if (s.isSelf) "your medicines" else "${s.personName.ifBlank { "their" }}'s medicines"
    Muted("DoseCircle will remind you about $who and keep a record of every dose.", Modifier.fillMaxWidth(), MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center))
}

@Composable
private fun TextBox(label: String, value: String, onChange: (String) -> Unit, ime: ImeAction, placeholder: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ime),
        modifier = Modifier.fillMaxWidth(),
    )
}
