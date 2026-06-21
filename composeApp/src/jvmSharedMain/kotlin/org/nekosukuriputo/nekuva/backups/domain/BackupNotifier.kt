package org.nekosukuriputo.nekuva.backups.domain

/**
 * Shows a system notification while a backup/restore runs in the background (Doki's
 * BaseBackupRestoreService notification) so the user can keep reading/exploring. Android = notification
 * channel; Desktop = system-tray balloon. Best-effort (no-op if notifications/tray unavailable).
 */
expect suspend fun notifyBackupStart(isRestore: Boolean)

expect suspend fun notifyBackupFinish(isRestore: Boolean, success: Boolean)
