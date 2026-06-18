package org.nekosukuriputo.nekuva.core.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.nekosukuriputo.nekuva.parsers.util.await
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Checks GitHub for a newer Nekuva release (port of Doki AppUpdateRepository, simplified for Nekuva's
 * single-repo releases instead of Kotatsu's `build-apps`/APK-variant flow). Compares the latest release tag
 * with the running version via [VersionId].
 */
class AppUpdateRepository(
    private val okHttp: OkHttpClient,
) {

    private val availableUpdate = MutableStateFlow<AppVersion?>(null)

    val isUpdateAvailable: Boolean get() = availableUpdate.value != null

    fun observeAvailableUpdate(): StateFlow<AppVersion?> = availableUpdate.asStateFlow()

    /** Returns a newer [AppVersion] than [currentVersionName], or null if up to date / unavailable. */
    suspend fun fetchUpdate(currentVersionName: String): AppVersion? = withContext(Dispatchers.Default) {
        runCatchingCancellable {
            val request = Request.Builder().get()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .build()
            val json = okHttp.newCall(request).await().use { JSONObject(it.body?.string().orEmpty()) }
            val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
            if (tag.isEmpty()) return@runCatchingCancellable null
            if (VersionId(tag) <= VersionId(currentVersionName)) return@runCatchingCancellable null
            val apkAsset = json.optJSONArray("assets")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            }
            AppVersion(
                id = json.optLong("id"),
                name = tag,
                url = json.optString("html_url"),
                apkSize = apkAsset?.optLong("size") ?: 0L,
                apkUrl = apkAsset?.optString("browser_download_url").orEmpty(),
                description = json.optString("body"),
            )
        }.getOrNull().also { availableUpdate.value = it }
    }

    private companion object {
        const val REPO = "NekoSukuriputo/nekuva"
    }
}
