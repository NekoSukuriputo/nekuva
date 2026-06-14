package org.nekosukuriputo.nekuva.reader.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.nekosukuriputo.nekuva.core.network.imageproxy.ImageProxyInterceptor
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ReaderMode
import org.nekosukuriputo.nekuva.core.util.ext.toFile
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.util.await
import java.util.zip.ZipFile
import kotlin.math.roundToInt

/**
 * Picks the reading mode for a manga (Doki's DetectReaderModeUseCase): per-manga saved mode wins;
 * otherwise, if detection is on and the default isn't webtoon, sample a page and switch to webtoon
 * when it's much taller than wide (a long-strip page).
 */
class DetectReaderModeUseCase(
	private val okHttpClient: OkHttpClient,
	private val imageProxy: ImageProxyInterceptor,
	private val mangaDataRepository: MangaDataRepository,
	private val settings: AppSettings,
) {

	suspend operator fun invoke(manga: Manga, pages: List<MangaPage>): ReaderMode {
		mangaDataRepository.getReaderMode(manga.id)?.let { return it }
		val default = settings.defaultReaderMode
		if (!settings.isReaderModeDetectionEnabled || default == ReaderMode.WEBTOON || pages.isEmpty()) {
			return default
		}
		val sample = pages.getOrNull((pages.size * 0.3f).roundToInt().coerceIn(0, pages.lastIndex)) ?: return default
		val bytes = runCatching { loadBytes(sample) }.getOrNull() ?: return default
		val (w, h) = decodeImageBounds(bytes) ?: return default
		val mode = if (w > 0 && w * MIN_WEBTOON_RATIO < h) ReaderMode.WEBTOON else default
		runCatching { mangaDataRepository.saveReaderMode(manga, mode) }
		return mode
	}

	private suspend fun loadBytes(page: MangaPage): ByteArray {
		val url = page.url
		return if (url.startsWith("http", ignoreCase = true)) {
			withContext(Dispatchers.IO) {
				imageProxy.interceptPageRequest(Request.Builder().url(url).build(), okHttpClient).use {
					it.body.bytes()
				}
			}
		} else {
			runInterruptible(Dispatchers.IO) {
				val u = java.net.URI(url)
				when (u.scheme) {
					"zip" -> {
						val entry = (u.fragment ?: error("no entry")).removePrefix("/")
						ZipFile(u.toFile()).use { zip ->
							val e = zip.getEntry(entry) ?: zip.getEntry("/$entry") ?: error("entry not found")
							zip.getInputStream(e).use { s -> s.readBytes() }
						}
					}
					"file", null -> u.toFile().readBytes()
					else -> error("unsupported scheme ${u.scheme}")
				}
			}
		}
	}

	private companion object {
		const val MIN_WEBTOON_RATIO = 1.8f
	}
}
