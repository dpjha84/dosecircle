@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.dosecircle.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dosecircle.app.ui.theme.LocalComfort
import com.dosecircle.app.ui.theme.MedPalette

@Composable
private fun buttonHeight(): Dp = if (LocalComfort.current) 76.dp else 64.dp

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(32.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = buttonHeight()),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    FilledTonalButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(32.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = buttonHeight() - 8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)),
        modifier = modifier.fillMaxWidth().heightIn(min = buttonHeight() - 8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerLowest, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) { content() }
}

/** Selectable chips that wrap onto new lines. [multi] decides whether tapping toggles or replaces. */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: Set<T>, onToggle: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((value, label) in options) {
            FilterChip(
                selected = value in selected, onClick = { onToggle(value) },
                label = { Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) },
            )
        }
    }
}

@Composable
fun Avatar(name: String, colorIndex: Int, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MedPalette[Math.floorMod(colorIndex, MedPalette.size)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(4.dp))
            Text("PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Banner(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Text(text, color = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

/** A pill-shaped label such as "Due now" or "Missed". */
@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).let { if (onClick != null) it.clickableRow(onClick) else it }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Muted(subtitle)
        }
        trailing()
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

@Composable
fun TimePickerDialog(initialMinutes: Int, is24Hour: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, description, tint = tint) }
}
