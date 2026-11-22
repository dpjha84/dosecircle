@file:OptIn(ExperimentalMaterial3Api::class)

package com.dosecircle.app.ui.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.BuildConfig
import com.dosecircle.app.billing.BillingEvent
import com.dosecircle.app.billing.ProOffer
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.Banner
import com.dosecircle.app.ui.components.IconAction
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.PrimaryButton
import com.dosecircle.app.ui.components.TonalButton
import com.dosecircle.app.ui.findActivity

private data class Perk(val icon: ImageVector, val title: String, val detail: String)

private val Perks = listOf(
    Perk(Icons.Rounded.Groups, "Your whole care circle", "Family and aides log doses under their own names, with a warning before anyone doubles up."),
    Perk(Icons.Rounded.Notifications, "Alert a chain of caregivers", "If a dose is missed, the next person is alerted until someone responds."),
    Perk(Icons.Rounded.Inventory2, "Refill reminders", "Supply counts down as doses are logged, with a heads-up before it runs out."),
    Perk(Icons.Rounded.PictureAsPdf, "Doctor reports", "A clear one-page PDF of medicines, adherence and notes for appointments."),
    Perk(Icons.Rounded.Person, "More than one person", "Look after a parent and a partner in one app."),
)

@Composable
fun PaywallScreen(reason: String, onClose: () -> Unit, vm: PaywallViewModel = appViewModel { PaywallViewModel(it) }) {
    val offers by vm.offers.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val selectedId by vm.selected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current.findActivity()
    val selected: ProOffer? = offers.firstOrNull { it.basePlanId == selectedId } ?: offers.firstOrNull { it.isAnnual } ?: offers.firstOrNull()

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                BillingEvent.Purchased -> onClose()
                BillingEvent.Pending -> snackbar.showSnackbar("Your payment is pending. Pro unlocks as soon as it completes.")
                BillingEvent.Cancelled -> Unit
                is BillingEvent.Failed -> snackbar.showSnackbar(e.message)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconAction(Icons.Rounded.Close, "Close", onClose) }

            Box(Modifier.align(Alignment.CenterHorizontally).size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WorkspacePremium, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text("DoseCircle Pro", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Muted("Care for the whole family, together.", Modifier.fillMaxWidth(), MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center))
            if (reason.isNotBlank()) { Spacer(Modifier.height(14.dp)); Banner(reason) }

            Spacer(Modifier.height(18.dp))
            Perks.forEach { p ->
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Muted(p.detail)
                    }
                }
            }
            Muted("Always free: unlimited medicines, reminders that keep nudging, full history and spreadsheet export.", Modifier.padding(top = 10.dp, bottom = 6.dp))

            Spacer(Modifier.height(12.dp))
            when {
                loading && offers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                offers.isEmpty() -> {
                    Banner("Subscriptions aren't available on this device right now. Check your connection and Google Play, then try again.", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
                    TonalButton("Try again", vm::reload, Modifier.padding(top = 10.dp))
                }
                else -> offers.forEach { o -> OfferCard(o, selected = o.basePlanId == selected?.basePlanId, onSelect = { vm.select(o.basePlanId) }) }
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = when {
                    selected == null -> "Unavailable"
                    selected.trialLabel != null -> "Start ${selected.trialLabel}"
                    else -> "Subscribe for ${selected.price} / ${selected.periodLabel}"
                },
                onClick = { if (activity != null && selected != null) vm.purchase(activity, selected) },
                enabled = selected != null && activity != null,
            )
            TextButton(onClick = { vm.restore { ok -> if (ok) onClose() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Restore purchases", style = MaterialTheme.typography.labelLarge)
            }
            Muted(
                (if (selected?.trialLabel != null) "Then ${selected.price} per ${selected.periodLabel}. " else "") +
                    "Renews automatically. Cancel any time in Google Play. Pro unlocks features only; your medicine data is never sold or shared.",
                Modifier.padding(vertical = 8.dp), MaterialTheme.typography.bodySmall,
            )
            if (BuildConfig.DEBUG) TonalButton("Debug: unlock Pro", vm::debugUnlock)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OfferCard(o: ProOffer, selected: Boolean, onSelect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).selectable(selected = selected, onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) cs.primaryContainer else cs.surfaceContainerLowest),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(if (o.isAnnual) "Yearly" else "Monthly", style = MaterialTheme.typography.titleMedium)
                Muted(listOfNotNull(o.trialLabel, if (o.isAnnual) "Best value" else null).joinToString(" · ").ifEmpty { "Cancel any time" })
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(o.price, style = MaterialTheme.typography.titleLarge)
                Muted("per ${o.periodLabel}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
