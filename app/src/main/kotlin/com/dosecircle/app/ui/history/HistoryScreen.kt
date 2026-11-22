@file:OptIn(ExperimentalMaterial3Api::class)

package com.dosecircle.app.ui.history

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.ChoiceChips
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.OutlineButton
import com.dosecircle.app.ui.components.ProBadge
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.core.HistoryOutcome

@Composable
fun HistoryScreen(onPaywall: (String) -> Unit, vm: HistoryViewModel = appViewModel { HistoryViewModel(it) }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm) { vm.share.collect { intent -> try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { snackbar.showSnackbar("No app available to share this file.") } } }
    LaunchedEffect(vm) { vm.paywall.collect { onPaywall(it) } }
    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                if (ui.person.isNotEmpty()) Muted("For ${ui.person}", style = MaterialTheme.typography.bodyLarge)
            }
            item { ChoiceChips(listOf(7 to "7 days", 30 to "30 days", 90 to "90 days"), setOf(ui.days), onToggle = vm::setPeriod) }
            item {
                SurfaceCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(ui.percentText, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                            Muted("taken as scheduled", Modifier.padding(bottom = 8.dp), MaterialTheme.typography.bodyLarge)
                        }
                        LinearProgressIndicator(
                            progress = { ui.fraction }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Muted(ui.summary, style = MaterialTheme.typography.bodyLarge)
                        if (ui.strip.isNotEmpty()) Strip(ui.strip)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Share as spreadsheet (CSV)", vm::exportCsv, icon = Icons.Rounded.Download)
                    Box {
                        OutlineButton("Doctor report (PDF)", vm::exportReport, icon = Icons.Rounded.PictureAsPdf)
                        if (!ui.isPro) ProBadge(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp))
                    }
                }
            }
            if (!ui.loading && ui.groups.isEmpty()) item { Muted("Doses you log will appear here.", style = MaterialTheme.typography.bodyLarge) }
            ui.groups.forEach { g ->
                item(key = "h-" + g.title) { Text(g.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp)) }
                items(g.rows, key = { it.key }) { RowItem(it) }
            }
        }
    }
}

@Composable
private fun Strip(days: List<DayDotUi>) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val color: Color = when (d.dot) {
                    DayDot.ALL_TAKEN -> cs.primary
                    DayDot.PARTIAL -> cs.secondary
                    DayDot.ALL_MISSED -> cs.error
                    DayDot.NONE -> cs.outlineVariant
                }
                Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                Text(d.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowItem(r: HistoryRowUi) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = when (r.outcome) {
        HistoryOutcome.TAKEN, HistoryOutcome.AS_NEEDED -> cs.primaryContainer to cs.onPrimaryContainer
        HistoryOutcome.TAKEN_LATE -> cs.secondaryContainer to cs.onSecondaryContainer
        HistoryOutcome.SKIPPED -> cs.surfaceVariant to cs.onSurfaceVariant
        HistoryOutcome.MISSED -> cs.errorContainer to cs.onErrorContainer
    }
    SurfaceCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Muted("${r.time}${if (r.detail.isNotEmpty()) " · ${r.detail}" else ""}")
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text(r.outcomeText, color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
