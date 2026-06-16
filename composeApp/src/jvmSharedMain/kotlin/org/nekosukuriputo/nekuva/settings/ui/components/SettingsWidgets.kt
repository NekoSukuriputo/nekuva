package org.nekosukuriputo.nekuva.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.save
import org.jetbrains.compose.resources.stringResource

/** Generic preference row: icon + title/summary + optional trailing control. */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = { onCheckedChange(it) }, enabled = enabled)
        },
    )
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A single-choice preference: shows the current selection as summary, opens a radio dialog on click. */
@Composable
fun <T> SettingsSingleChoice(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selected }?.first
    SettingsItem(
        title = title,
        summary = selectedLabel,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                // Scrollable + height-capped so long lists (e.g. 9 color themes) don't overflow the
                // dialog and leave the bottom options unreachable.
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    for ((label, value) in options) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = value == selected, onClick = {
                                    onSelect(value)
                                    showDialog = false
                                })
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            RadioButton(selected = value == selected, onClick = {
                                onSelect(value)
                                showDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

/**
 * Text-entry preference (Doki's EditTextPreference): a row showing the current value as summary; tapping
 * opens a dialog with a single-line field. [isPassword] masks both the summary and the field.
 */
@Composable
fun SettingsEditText(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = when {
        value.isEmpty() -> null
        isPassword -> "•".repeat(value.length.coerceIn(1, 8))
        else -> value
    }
    SettingsItem(title = title, summary = summary, enabled = enabled, onClick = { showDialog = true })
    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = placeholder?.let { { Text(it) } },
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false; onValueChange(text) }) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

/** Slider preference with a value label (e.g. grid size, sensitivity). */
@Composable
fun SettingsSlider(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    valueLabel: String = value.toString(),
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val steps = if (step > 0) ((valueRange.last - valueRange.first) / step - 1).coerceAtLeast(0) else 0
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps,
            enabled = enabled,
        )
    }
}

/** Multi-select preference: summary shows the chosen labels, a dialog with checkboxes edits the set. */
@Composable
fun <T> SettingsMultiChoice(
    title: String,
    options: List<Pair<String, T>>,
    selected: Set<T>,
    onConfirm: (Set<T>) -> Unit,
    emptySummary: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = options.filter { it.second in selected }.joinToString(", ") { it.first }
    SettingsItem(title = title, summary = summary.ifEmpty { emptySummary }, onClick = { showDialog = true })
    if (showDialog) {
        var working by remember { mutableStateOf(selected) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                // Scrollable + height-capped so long lists (e.g. 9 color themes) don't overflow the
                // dialog and leave the bottom options unreachable.
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    for ((label, value) in options) {
                        val checked = value in working
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = checked, onClick = {
                                    working = if (checked) working - value else working + value
                                })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                working = if (it) working + value else working - value
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false; onConfirm(working) }) {
                    Text(stringResource(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
