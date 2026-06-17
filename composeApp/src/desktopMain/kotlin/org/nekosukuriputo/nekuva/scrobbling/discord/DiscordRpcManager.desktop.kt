package org.nekosukuriputo.nekuva.scrobbling.discord

import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.scrobbling.discord.data.DiscordRepository

/** Desktop has no Discord RPC integration here (KizzyRPC is Android-only) → no-op. */
actual class DiscordRpcManager actual constructor(
    settings: AppSettings,
    repository: DiscordRepository,
) {
    actual fun updateRpc(manga: Manga, chapterNumber: Int, chaptersTotal: Int) = Unit
    actual fun setIdle() = Unit
    actual fun clearRpc() = Unit
}
