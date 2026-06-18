package org.nekosukuriputo.nekuva.backups.work

/** Desktop has no WorkManager — periodic backup is Android-only for now. */
actual fun scheduleBackup() = Unit
