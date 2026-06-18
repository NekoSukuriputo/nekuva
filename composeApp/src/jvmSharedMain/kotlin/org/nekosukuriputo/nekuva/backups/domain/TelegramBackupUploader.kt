package org.nekosukuriputo.nekuva.backups.domain

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.util.await
import java.io.File

/** Uploads periodic backups to Telegram via the Bot API (port of Doki TelegramBackupUploader). */
class TelegramBackupUploader(
    private val settings: AppSettings,
    private val client: OkHttpClient,
) {

    private val botToken get() = TelegramBackupConfig.BOT_TOKEN

    val isAvailable: Boolean get() = TelegramBackupConfig.isAvailable

    /** Public Telegram link to the configured bot (opened from the settings screen). */
    val botUrl: String get() = "https://t.me/${TelegramBackupConfig.BOT_NAME}"

    suspend fun uploadBackup(file: File) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", requireChatId())
            .addFormDataPart("document", file.name, file.asRequestBody("application/zip".toMediaTypeOrNull()))
            .build()
        client.newCall(Request.Builder().url(urlOf("sendDocument").build()).post(body).build()).await().consume()
    }

    suspend fun sendTestMessage(message: String) {
        client.newCall(Request.Builder().url(urlOf("getMe").build()).build()).await().consume()
        val url = urlOf("sendMessage")
            .addQueryParameter("chat_id", requireChatId())
            .addQueryParameter("text", message)
            .build()
        client.newCall(Request.Builder().url(url).build()).await().consume()
    }

    private fun requireChatId() = checkNotNull(settings.backupTelegramChatId) { "Telegram chat ID not set in settings" }

    private fun Response.consume() = use {
        if (it.isSuccessful) return
        val jo = JSONObject(it.body?.string().orEmpty())
        if (!jo.optBoolean("ok", true)) throw RuntimeException(jo.optString("description"))
    }

    private fun urlOf(method: String) = HttpUrl.Builder()
        .scheme("https")
        .host("api.telegram.org")
        .addPathSegment("bot$botToken")
        .addPathSegment(method)
}
