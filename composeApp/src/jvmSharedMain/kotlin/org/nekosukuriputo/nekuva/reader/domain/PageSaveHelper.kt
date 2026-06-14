package org.nekosukuriputo.nekuva.reader.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.core.network.imageproxy.ImageProxyInterceptor
import org.nekosukuriputo.nekuva.core.util.ext.toFile
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import java.util.zip.ZipFile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Fetches a reader page's original bytes and hands them to the platform [PagePersister].
 * KMP port of Doki's PageSaveHelper: handles remote pages (OkHttp via the image proxy) and
 * downloaded pages (`zip:`/`file:` URIs — read straight off disk to keep the original format).
 */
@OptIn(ExperimentalTime::class)
class PageSaveHelper(
	private val okHttpClient: OkHttpClient,
	private val imageProxy: ImageProxyInterceptor,
	private val persister: PagePersister,
) {

	suspend fun save(page: MangaPage): String? {
		val (bytes, ext) = loadBytes(page)
		return persister.savePage(bytes, buildFileName(ext), mimeOf(ext))
	}

	suspend fun share(page: MangaPage): String? {
		val (bytes, ext) = loadBytes(page)
		return persister.sharePage(bytes, buildFileName(ext), mimeOf(ext))
	}

	/** @return raw image bytes + a file extension (no dot). */
	private suspend fun loadBytes(page: MangaPage): Pair<ByteArray, String> {
		val url = page.url
		return when {
			url.startsWith("http", ignoreCase = true) -> withContext(Dispatchers.IO) {
				val response = imageProxy.interceptPageRequest(Request.Builder().url(url).build(), okHttpClient)
				response.use {
					check(it.isSuccessful) { "HTTP ${it.code}" }
					val bytes = it.body.bytes()
					bytes to extOf(it.header("Content-Type"), url)
				}
			}

			else -> runInterruptible(Dispatchers.IO) {
				val u = java.net.URI(url)
				when (u.scheme) {
					"zip" -> {
						val entryName = (u.fragment ?: error("No zip entry in $url")).removePrefix("/")
						ZipFile(u.toFile()).use { zip ->
							val entry = zip.getEntry(entryName) ?: zip.getEntry("/$entryName")
							?: error("Entry $entryName not found")
							zip.getInputStream(entry).use { it.readBytes() } to extFromName(entryName)
						}
					}

					"file", null -> {
						val file = u.toFile()
						file.readBytes() to extFromName(file.name)
					}

					else -> error("Unsupported page URI scheme: ${u.scheme}")
				}
			}
		}
	}

	private fun buildFileName(ext: String): String = "nekuva_page_${Clock.System.now().toEpochMilliseconds()}.$ext"

	private fun extFromName(name: String): String =
		name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 4 } ?: "jpg"

	private fun extOf(contentType: String?, url: String): String = when {
		contentType == null -> extFromName(url.substringBefore('?'))
		contentType.contains("png") -> "png"
		contentType.contains("webp") -> "webp"
		contentType.contains("gif") -> "gif"
		contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
		else -> extFromName(url.substringBefore('?'))
	}

	private fun mimeOf(ext: String): String = when (ext) {
		"png" -> "image/png"
		"webp" -> "image/webp"
		"gif" -> "image/gif"
		"jpg", "jpeg" -> "image/jpeg"
		else -> "image/jpeg"
	}
}
