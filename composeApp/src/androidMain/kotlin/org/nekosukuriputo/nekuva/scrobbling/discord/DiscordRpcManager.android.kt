package org.nekosukuriputo.nekuva.scrobbling.discord

import com.my.kizzyrpc.KizzyRPC
import com.my.kizzyrpc.entities.presence.Activity
import com.my.kizzyrpc.entities.presence.Assets
import com.my.kizzyrpc.entities.presence.Metadata
import com.my.kizzyrpc.entities.presence.Timestamps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.scrobbling.common.ScrobblerConfig
import org.nekosukuriputo.nekuva.scrobbling.discord.data.DiscordRepository

private const val APP_NAME = "Nekuva"
private const val STATUS_ONLINE = "online"
private const val STATUS_IDLE = "idle"
private const val ACTIVITY_TYPE_WATCHING = 3

/** Android Discord Rich Presence via KizzyRPC gateway (port of Doki's DiscordRpc). */
actual class DiscordRpcManager actual constructor(
    private val settings: AppSettings,
    private val repository: DiscordRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var rpc: KizzyRPC? = null
    private var startTime: Long = 0L
    private var updateJob: Job? = null

    @Synchronized
    actual fun clearRpc() {
        runCatching { rpc?.closeRPC() }
        rpc = null
        startTime = 0L
    }

    actual fun setIdle() {
        val client = rpc ?: return
        scope.launch { runCatchingCancellable { client.updateRPC(buildIdleActivity(), STATUS_IDLE, startTime) } }
    }

    actual fun updateRpc(manga: Manga, chapterNumber: Int, chaptersTotal: Int) {
        val client = getRpc() ?: return
        if (settings.isDiscordRpcSkipNsfw && manga.isNsfw()) {
            clearRpc()
            return
        }
        if (startTime == 0L) startTime = System.currentTimeMillis()
        val cover: String? = manga.coverUrl
        val prev = updateJob
        updateJob = scope.launch {
            prev?.cancelAndJoin()
            val largeImage = cover?.let { runCatchingCancellable { repository.getMediaProxyUrl(it) }.getOrNull() ?: it }
            runCatchingCancellable {
                client.updateRPC(
                    activity = Activity(
                        applicationId = ScrobblerConfig.DISCORD_APP_ID,
                        name = APP_NAME,
                        details = manga.title,
                        state = "Chapter $chapterNumber of $chaptersTotal",
                        type = ACTIVITY_TYPE_WATCHING,
                        timestamps = Timestamps(start = startTime),
                        assets = Assets(
                            largeImage = largeImage,
                            largeText = "Reading ${manga.title}",
                            smallText = APP_NAME,
                            smallImage = null,
                        ),
                        buttons = listOf("Read on ${manga.source.name}"),
                        metadata = Metadata(listOf(manga.publicUrl)),
                    ),
                    status = STATUS_ONLINE,
                    since = startTime,
                )
            }
        }
    }

    private fun buildIdleActivity() = Activity(
        applicationId = ScrobblerConfig.DISCORD_APP_ID,
        name = APP_NAME,
        type = ACTIVITY_TYPE_WATCHING,
        timestamps = Timestamps(start = startTime),
    )

    @Synchronized
    private fun getRpc(): KizzyRPC? {
        rpc?.let { return it }
        if (!settings.isDiscordRpcEnabled) return null
        val token = settings.discordToken ?: return null
        return KizzyRPC(token).also { rpc = it }
    }
}
