package org.nekosukuriputo.nekuva.backups.data

/**
 * Raw read/write of the app's main preference store (Doki settings backup). The BackupRepository splits
 * the dump into the "settings" and "reader_grid" (tap_grid_* keys) sections and drops sensitive keys.
 * Android: the `nekuva_prefs` SharedPreferences; Desktop: the `Nekuva` Preferences node.
 */
expect fun dumpAppPreferences(): Map<String, Any?>

expect fun writeAppPreferences(values: Map<String, Any?>)
