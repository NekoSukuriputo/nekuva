package org.nekosukuriputo.nekuva.backups.domain

// Desktop has no "All files access" permission; restored dirs are read directly. Nothing to do.
actual suspend fun ensureLocalStorageAccessAfterRestore() = Unit
