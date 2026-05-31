package org.nekosukuriputo.nekuva.core.parser

import androidx.collection.MutableLongSet
import coil3.request.CachePolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import org.nekosukuriputo.nekuva.core.cache.MemoryContentCache
import org.nekosukuriputo.nekuva.core.cache.SafeDeferred
import org.nekosukuriputo.nekuva.core.util.MultiMutex
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

abstract class CachingMangaRepository(
	private val cache: MemoryContentCache,
) : MangaRepository {

	private val detailsMutex = MultiMutex<Long>()
	private val relatedMangaMutex = MultiMutex<Long>()
	private val pagesMutex = MultiMutex<Long>()

	final override suspend fun getDetails(manga: Manga): Manga = getDetails(manga, CachePolicy.ENABLED)

	final override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = pagesMutex.withLock(chapter.id) {
		cache.getPages(source, chapter.url)?.let { return it }
		val pages = asyncSafe {
			getPagesImpl(chapter).distinctById()
		}
		cache.putPages(source, chapter.url, pages)
		pages
	}.await()

	final override suspend fun getRelated(seed: Manga): List<Manga> = relatedMangaMutex.withLock(seed.id) {
		cache.getRelatedManga(source, seed.url)?.let { return it }
		val related = asyncSafe {
			getRelatedMangaImpl(seed).filterNot { it.id == seed.id }
		}
		cache.putRelatedManga(source, seed.url, related)
		related
	}.await()

	suspend fun getDetails(manga: Manga, cachePolicy: CachePolicy): Manga = detailsMutex.withLock(manga.id) {
		if (cachePolicy.readEnabled) {
			cache.getDetails(source, manga.url)?.let { return it }
		}
		val details = asyncSafe {
			getDetailsImpl(manga)
		}
		if (cachePolicy.writeEnabled) {
			cache.putDetails(source, manga.url, details)
		}
		details
	}.await()

	suspend fun peekDetails(manga: Manga): Manga? {
		return cache.getDetails(source, manga.url)
	}

	fun invalidateCache() {
		cache.clear(source)
	}

	protected abstract suspend fun getDetailsImpl(manga: Manga): Manga

	protected abstract suspend fun getRelatedMangaImpl(seed: Manga): List<Manga>

	protected abstract suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage>

	@OptIn(ExperimentalStdlibApi::class)
	private suspend fun <T> asyncSafe(block: suspend CoroutineScope.() -> T): SafeDeferred<T> {
		var dispatcher = currentCoroutineContext()[CoroutineDispatcher.Key]
		if (dispatcher == null || dispatcher is MainCoroutineDispatcher) {
			dispatcher = Dispatchers.Default
		}
		return SafeDeferred(
			@OptIn(DelicateCoroutinesApi::class) GlobalScope.async(dispatcher) {
				runCatchingCancellable { block() }
			},
		)
	}

	private fun List<MangaPage>.distinctById(): List<MangaPage> {
		if (isEmpty()) {
			return emptyList()
		}
		val result = ArrayList<MangaPage>(size)
		val set = MutableLongSet(size)
		for (page in this) {
			if (set.add(page.id)) {
				result.add(page)
			} else if (false) {
				println("Duplicate page: $page")
			}
		}
		return result
	}
}





