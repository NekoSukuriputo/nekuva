package org.nekosukuriputo.nekuva.core.cache

import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import java.util.concurrent.TimeUnit

class MemoryContentCache {

	private val detailsCache = ExpiringLruCache<SafeDeferred<Manga>>(4, 5, TimeUnit.MINUTES)
	private val pagesCache =
		ExpiringLruCache<SafeDeferred<List<MangaPage>>>(4, 10, TimeUnit.MINUTES)
	private val relatedMangaCache =
		ExpiringLruCache<SafeDeferred<List<Manga>>>(3, 10, TimeUnit.MINUTES)

	suspend fun getDetails(source: MangaSource, url: String): Manga? {
		return detailsCache[Key(source, url)]?.awaitOrNull()
	}

	fun putDetails(source: MangaSource, url: String, result: SafeDeferred<Manga>) {
		detailsCache[Key(source, url)] = result
	}

	suspend fun getPages(source: MangaSource, url: String): List<MangaPage>? {
		return pagesCache[Key(source, url)]?.awaitOrNull()
	}

	fun putPages(source: MangaSource, url: String, result: SafeDeferred<List<MangaPage>>) {
		pagesCache[Key(source, url)] = result
	}

	suspend fun getRelatedManga(source: MangaSource, url: String): List<Manga>? {
		return relatedMangaCache[Key(source, url)]?.awaitOrNull()
	}

	fun putRelatedManga(source: MangaSource, url: String, result: SafeDeferred<List<Manga>>) {
		relatedMangaCache[Key(source, url)] = result
	}

	fun clear(source: MangaSource) {
		clearCache(detailsCache, source)
		clearCache(pagesCache, source)
		clearCache(relatedMangaCache, source)
	}

	private fun clearCache(cache: ExpiringLruCache<*>, source: MangaSource) {
		cache.removeAll(source)
	}

	data class Key(
		val source: MangaSource,
		val url: String,
	)
}
