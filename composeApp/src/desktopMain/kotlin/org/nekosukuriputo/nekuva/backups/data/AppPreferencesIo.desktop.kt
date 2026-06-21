package org.nekosukuriputo.nekuva.backups.data

import java.util.prefs.Preferences

private fun node(): Preferences = Preferences.userRoot().node("Nekuva")

actual fun dumpAppPreferences(): Map<String, Any?> {
    val n = node()
    return runCatching { n.keys().associateWith { n.get(it, null) as Any? } }.getOrDefault(emptyMap())
}
