package org.nekosukuriputo.nekuva.scrobbling.discord

import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.scrobbling.discord.data.DiscordRepository

/**
 * Discord Rich Presence controller (Doki's DiscordRpc). Android = real gateway via KizzyRPC; Desktop has
 * no equivalent concept here → no-op actual. Driven by the reader as the read chapter changes.
 */
expect class DiscordRpcManager(settings: AppSettings, repository: DiscordRepository) {

    /** Publish/refresh the "now reading" presence for [manga] at chapter [chapterNumber]/[chaptersTotal]. */
    fun updateRpc(manga: Manga, chapterNumber: Int, chaptersTotal: Int)

    /** Mark the presence idle (e.g. reader paused). */
    fun setIdle()

    /** Tear down the gateway connection (reader closed / RPC disabled). */
    fun clearRpc()
}
