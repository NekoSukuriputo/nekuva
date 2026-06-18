package org.nekosukuriputo.nekuva.backups.work

/**
 * (Re)schedule the periodic backup (Doki PeriodicalBackupWorker.Scheduler). Android = WorkManager with the
 * interval from `backup_periodic_freq`; Desktop = no-op. Idempotent — safe to call on every app start.
 */
expect fun scheduleBackup()
