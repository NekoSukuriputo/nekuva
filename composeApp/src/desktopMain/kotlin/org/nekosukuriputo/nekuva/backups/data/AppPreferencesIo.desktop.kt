package org.nekosukuriputo.nekuva.backups.data

import java.util.prefs.Preferences

private fun node(): Preferences = Preferences.userRoot().node("Nekuva")

// java.util.prefs stores everything as strings; multiplatform-settings' PreferencesSettings reads them
// back with type coercion (getBoolean("true"), getInt("123"), …), so a string round-trip is correct.
actual fun dumpAppPreferences(): Map<String, Any?> {
    val n = node()
    return runCatching { n.keys().associateWith { n.get(it, null) as Any? } }.getOrDefault(emptyMap())
}

actual fun writeAppPreferences(values: Map<String, Any?>) {
    val n = node()
    for ((key, value) in values) {
        if (value != null) n.put(key, value.toString())
    }
    runCatching { n.flush() }
}
