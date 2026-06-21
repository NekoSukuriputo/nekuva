package org.nekosukuriputo.nekuva.backups.data

import com.russhwolf.settings.ObservableSettings
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.prefs.putStringSet

/**
 * Reads the app's main preference store for the settings backup (raw; no generic get exists on
 * multiplatform-settings, so this is platform-specific). Android: `nekuva_prefs` SharedPreferences;
 * Desktop: the `Nekuva` Preferences node.
 */
expect fun dumpAppPreferences(): Map<String, Any?>

/**
 * Writes restored settings back THROUGH the app's [ObservableSettings] (not the raw platform store) so
 * each value uses Nekuva's own encoding — in particular set-valued prefs are stored as a comma-joined
 * string (SettingsExt.putStringSet). A backup from another kotatsu app may store such a pref as a JSON
 * array (`[1,2]`); writing it via putStringSet converts it to Nekuva's "1,2" form, instead of the raw
 * "[1, 2]" string that crashed getMangaListBadges (`"[1".toInt()`).
 */
fun writeAppPreferences(values: Map<String, Any?>) {
    val settings = GlobalContext.get().get<ObservableSettings>()
    for ((key, value) in values) {
        when (value) {
            is Boolean -> settings.putBoolean(key, value)
            is Int -> settings.putInt(key, value)
            is Long -> settings.putLong(key, value)
            is Float -> settings.putFloat(key, value)
            is Double -> settings.putDouble(key, value)
            is String -> settings.putString(key, value)
            is Collection<*> -> settings.putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> {}
        }
    }
}
