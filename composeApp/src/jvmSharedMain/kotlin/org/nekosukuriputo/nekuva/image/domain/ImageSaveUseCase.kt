package org.nekosukuriputo.nekuva.image.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.reader.domain.PagePersister

/**
 * Save / share an image by URL (Doki ImageViewModel.saveImage). Downloads the bytes via the app OkHttp
 * client (source headers/interceptors applied) and hands them to the platform [PagePersister] — Android
 * writes to the MediaStore/SAF, Desktop to a Pictures/Nekuva folder. Returns the saved location label.
 */
class ImageSaveUseCase(
    private val httpClient: OkHttpClient,
    private val pagePersister: PagePersister,
) {

    suspend fun save(url: String): String? = persist(url, share = false)

    suspend fun share(url: String): String? = persist(url, share = true)

    private suspend fun persist(url: String, share: Boolean): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull() ?: return@withContext null
        val name = fileNameOf(url)
        val mime = mimeOf(name)
        if (share) pagePersister.sharePage(bytes, name, mime) else pagePersister.savePage(bytes, name, mime)
    }

    private fun fileNameOf(url: String): String {
        val raw = url.substringBefore('?').substringAfterLast('/').ifEmpty { "image" }
        return if ('.' in raw) raw else "$raw.jpg"
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
