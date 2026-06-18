package org.nekosukuriputo.nekuva.backups.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.nekosukuriputo.nekuva.backups.data.BackupRepository
import org.nekosukuriputo.nekuva.backups.domain.TelegramBackupUploader
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Background periodic backup (Doki PeriodicalBackupWorker): writes a timestamped backup to the configured
 * directory (or the app's external files dir), trims old ones, records the time, and optionally uploads to
 * Telegram. Instantiated by WorkManager; deps via Koin.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val settings = get<AppSettings>()
        if (!settings.isPeriodicalBackupEnabled) return Result.success()
        val repository = get<BackupRepository>()
        val dir = settings.periodicalBackupDirectory
            ?: applicationContext.getExternalFilesDir("backups")?.absolutePath
            ?: return Result.failure()

        val file = runCatchingCancellable {
            repository.createBackupToDirectory(dir, settings.periodicalBackupMaxCount)
        }.getOrNull() ?: return Result.retry()
        settings.setPref(AppSettings.KEY_BACKUP_PERIODICAL_LAST, System.currentTimeMillis().toString())

        if (settings.isBackupTelegramUploadEnabled && settings.backupTelegramChatId != null) {
            val uploader = get<TelegramBackupUploader>()
            if (uploader.isAvailable) {
                runCatchingCancellable { uploader.uploadBackup(file) }
            }
        }
        return Result.success()
    }
}
