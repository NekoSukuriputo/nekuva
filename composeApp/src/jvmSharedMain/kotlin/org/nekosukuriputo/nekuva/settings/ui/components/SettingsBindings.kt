package org.nekosukuriputo.nekuva.settings.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

/** Switch bound to a boolean preference key (reads initial, persists on change). */
@Composable
fun BoolPref(settings: AppSettings, key: String, title: String, summary: String? = null, default: Boolean = false) {
    var value by remember { mutableStateOf(settings.prefBoolean(key, default)) }
    SettingsSwitch(title = title, summary = summary, checked = value, onCheckedChange = { settings.setPref(key, it); value = it })
}

/** Single-choice list bound to a preference that stores the selected option's index (as a string). */
@Composable
fun IndexListPref(settings: AppSettings, key: String, title: String, options: List<String>, default: Int = 0) {
    var value by remember { mutableStateOf(settings.prefString(key, default.toString()).toIntOrNull() ?: default) }
    SettingsSingleChoice(
        title = title,
        options = options.mapIndexed { index, label -> label to index },
        selected = value,
        onSelect = { settings.setPref(key, it.toString()); value = it },
    )
}

/** Multi-select bound to a string-set preference. */
@Composable
fun MultiPref(settings: AppSettings, key: String, title: String, options: List<Pair<String, String>>, default: Set<String> = emptySet()) {
    var value by remember { mutableStateOf(settings.prefStringSet(key, default)) }
    SettingsMultiChoice(title = title, options = options, selected = value, onConfirm = { settings.setPref(key, it); value = it })
}
