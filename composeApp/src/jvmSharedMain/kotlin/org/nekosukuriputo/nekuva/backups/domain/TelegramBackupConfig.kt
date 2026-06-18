package org.nekosukuriputo.nekuva.backups.domain

/**
 * Telegram backup-upload credentials (Doki `tg_backup_bot_token`/`tg_backup_bot_name`).
 *
 * ⚠️ Empty by default. Unlike OAuth client ids, a Telegram **bot token is a secret** that fully controls the
 * bot, so Doki's is NOT reused here. To enable "send backups to Telegram":
 *   1. Create your own bot via @BotFather, copy its token into [BOT_TOKEN] and its @username into [BOT_NAME];
 *   2. Message your bot once and put your chat id in Settings ▸ Backup ▸ Periodic ▸ Telegram.
 * While [BOT_TOKEN] is empty the whole Telegram section stays hidden ([isAvailable] = false), matching Doki.
 */
object TelegramBackupConfig {
    // TODO(credentials): paste your own Telegram bot token + @username to enable Telegram backup upload.
    const val BOT_TOKEN: String = ""
    const val BOT_NAME: String = ""

    val isAvailable: Boolean get() = BOT_TOKEN.isNotEmpty()
}
