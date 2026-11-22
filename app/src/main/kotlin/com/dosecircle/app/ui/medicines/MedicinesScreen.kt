@file:OptIn(ExperimentalMaterial3Api::class)

package com.dosecircle.app.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosecircle.app.ui.appViewModel
import com.dosecircle.app.ui.components.Muted
import com.dosecircle.app.ui.components.StatusPill
import com.dosecircle.app.ui.components.SurfaceCard
import com.dosecircle.app.ui.theme.MedPalette

@Composable
fun MedicinesScreen(onOpen: (String) -> Unit, onAdd: () -> Unit, vm: MedicinesViewModel = appViewModel { MedicinesViewModel(it) }) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (ui.canEdit) ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add medicine", style = MaterialTheme.typography.labelLarge) })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Medicines", style = MaterialTheme.typography.headlineMedium)
                if (ui.person.isNotEmpty()) Muted("For ${ui.person}", style = MaterialTheme.typography.bodyLarge)
            }
            if (!ui.loading && ui.active.isEmpty() && ui.asNeeded.isEmpty() && ui.stopped.isEmpty()) {
                item { Muted("Nothing here yet. ${if (ui.canEdit) "Tap “Add medicine” to start." else "Ask the main caregiver to add medicines."}", style = MaterialTheme.typography.bodyLarge) }
            }
            items(ui.active, key = { it.id }) { MedRow(it, ui.canEdit, onOpen) }
            if (ui.asNeeded.isNotEmpty()) {
                item { Text("When needed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
                items(ui.asNeeded, key = { it.id }) { MedRow(it, ui.canEdit, onOpen) }
            }
            if (ui.stopped.isNotEmpty()) {
                item { Text("Stopped", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
                items(ui.stopped, key = { it.id }) { MedRow(it, ui.canEdit, onOpen) }
            }
        }
    }
}

@Composable
private fun MedRow(m: MedRowUi, canEdit: Boolean, onOpen: (String) -> Unit) {
    SurfaceCard(Modifier.then(if (canEdit) Modifier.clickable { onOpen(m.id) } else Modifier)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(MedPalette[Math.floorMod(m.colorIndex, MedPalette.size)]))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium)
                Muted(m.schedule)
                if (m.supply != null) Muted(m.supply)
            }
            if (m.critical) StatusPill("Important", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
