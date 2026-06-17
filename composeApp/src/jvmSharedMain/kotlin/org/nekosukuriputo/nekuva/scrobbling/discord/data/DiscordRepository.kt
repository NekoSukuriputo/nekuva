package org.nekosukuriputo.nekuva.scrobbling.discord.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.util.await
import org.nekosukuriputo.nekuva.scrobbling.common.ScrobblerConfig
import java.io.IOException

private const val SCHEME_MP = "mp:"

/**
 * Discord REST glue for Rich Presence (port of Doki's DiscordRepository): resolves external image URLs to
 * Discord media-proxy (`mp:`) paths and validates the captured user token. The gateway/presence itself is
 * the platform [org.nekosukuriputo.nekuva.scrobbling.discord.DiscordRpcManager] (Android KizzyRPC).
 */
class DiscordRepository(
    private val settings: AppSettings,
    private val httpClient: OkHttpClient,
) {

    private val appId get() = ScrobblerConfig.DISCORD_APP_ID

    fun isMediaProxyUrl(url: String) = url.startsWith(SCHEME_MP)

    suspend fun getMediaProxyUrl(url: String): String? {
        if (isMediaProxyUrl(url)) return url
        val token = checkNotNull(settings.discordToken) { "Discord token is missing" }
        val request = Request.Builder()
            .url("https://discord.com/api/v10/applications/$appId/external-assets")
            .header("Authorization", token)
            .post("{\"urls\":[\"$url\"]}".toRequestBody("application/json".toMediaType()))
            .build()
        val body = httpClient.newCall(request).await().use { it.body?.string().orEmpty() }
        val trimmed = body.trimStart()
        if (trimmed.startsWith("{")) {
            throw IOException(JSONObject(body).optString("message", "Discord error"))
        }
        val arr = JSONArray(body)
        val path = arr.optJSONObject(0)?.optString("external_asset_path").orEmpty()
        return if (path.isEmpty()) null else SCHEME_MP + path
    }

    suspend fun checkToken(token: String) {
        val request = Request.Builder()
            .url("https://discord.com/api/v10/users/@me")
            .header("Authorization", token)
            .get()
            .build()
        httpClient.newCall(request).await().use {
            if (!it.isSuccessful) throw IOException("Invalid token (${it.code})")
        }
    }
}
