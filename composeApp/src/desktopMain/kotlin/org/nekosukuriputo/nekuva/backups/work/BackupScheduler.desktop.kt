package org.nekosukuriputo.nekuva.backups.work

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.backups.data.BackupRepository
import org.nekosukuriputo.nekuva.backups.domain.TelegramBackupUploader
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Desktop has no WorkManager, so periodic backup is a **check-on-launch**: if it's enabled, a directory is set,
 * and at least one frequency interval has elapsed since the last backup, create one now (+ optional Telegram).
 */
actual fun scheduleBackup() {
    val koin = GlobalContext.getOrNull() ?: return
    val settings = koin.get<AppSettings>()
    if (!settings.isPeriodicalBackupEnabled) return
    val dir = settings.periodicalBackupDirectory ?: return
    val last = settings.prefString(AppSettings.KEY_BACKUP_PERIODICAL_LAST, "").toLongOrNull() ?: 0L
    if (System.currentTimeMillis() - last < settings.periodicalBackupFrequencyMillis) return

    val repository = koin.get<BackupRepository>()
    backupScope.launch {
        runCatching {
            val file = repository.createBackupToDirectory(dir, settings.periodicalBackupMaxCount) ?: return@launch
            settings.setPref(AppSettings.KEY_BACKUP_PERIODICAL_LAST, System.currentTimeMillis().toString())
            if (settings.isBackupTelegramUploadEnabled && settings.backupTelegramChatId != null) {
                val uploader = koin.get<TelegramBackupUploader>()
                if (uploader.isAvailable) runCatching { uploader.uploadBackup(file) }
            }
        }
    }
}
