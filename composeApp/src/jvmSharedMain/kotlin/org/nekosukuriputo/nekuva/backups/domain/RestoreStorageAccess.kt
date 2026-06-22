package org.nekosukuriputo.nekuva.backups.domain

/**
 * Called after a successful restore. A restored backup can bring in external local-manga directories
 * (`local_storage` / `local_manga_dirs`, e.g. `/storage/emulated/0/Manga`), but reading those raw paths
 * needs a platform permission (Android "All files access"). The manual "Add directory" flow nudges for
 * it; restore must do the same, otherwise the restored dirs stay unreadable and no local manga appear.
 *
 * Android: if a restored dir is on shared storage and All-files access isn't granted, open the system
 * grant screen. Desktop: no-op (no such permission).
 */
expect suspend fun ensureLocalStorageAccessAfterRestore()
