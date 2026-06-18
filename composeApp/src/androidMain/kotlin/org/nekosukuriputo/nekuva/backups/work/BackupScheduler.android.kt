package org.nekosukuriputo.nekuva.backups.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import java.util.concurrent.TimeUnit

private const val BACKUP_WORK = "nekuva_periodic_backup"

/**
 * Android periodic backup (Doki PeriodicalBackupWorker.Scheduler): schedules a [BackupWorker] whose interval
 * comes from `backup_periodic_freq`. Telegram upload adds a CONNECTED constraint. Cancels when disabled.
 */
actual fun scheduleBackup() {
    val koin = GlobalContext.getOrNull() ?: return
    val context = koin.get<Context>()
    val settings = koin.get<AppSettings>()
    val wm = WorkManager.getInstance(context)

    if (!settings.isPeriodicalBackupEnabled) {
        wm.cancelUniqueWork(BACKUP_WORK)
        return
    }
    // WorkManager enforces a 15-minute minimum periodic interval.
    val intervalMs = settings.periodicalBackupFrequencyMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(15))
    val builder = PeriodicWorkRequestBuilder<BackupWorker>(intervalMs, TimeUnit.MILLISECONDS)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
    if (settings.isBackupTelegramUploadEnabled) {
        builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    }
    wm.enqueueUniquePeriodicWork(BACKUP_WORK, ExistingPeriodicWorkPolicy.UPDATE, builder.build())
}
