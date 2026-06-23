package org.nekosukuriputo.nekuva.core.parser

import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilterCapabilities
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilterOptions
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.SortOrder

interface MangaRepository {
	val source: MangaSource
	val sortOrders: Set<SortOrder>
	var defaultSortOrder: SortOrder
	val filterCapabilities: MangaListFilterCapabilities

	suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga>
	suspend fun getDetails(manga: Manga): Manga
	suspend fun getPages(chapter: MangaChapter): List<MangaPage>
	suspend fun getPageUrl(page: MangaPage): String
	suspend fun getFilterOptions(): MangaListFilterOptions
	suspend fun getRelated(seed: Manga): List<Manga>

	suspend fun find(manga: Manga): Manga? {
		val list = getList(0, SortOrder.RELEVANCE, MangaListFilter(query = manga.title))
		return list.find { x -> x.id == manga.id }
	}

	class Factory(
		private val loaderContext: org.nekosukuriputo.nekuva.parsers.MangaLoaderContext,
		private val contentCache: org.nekosukuriputo.nekuva.core.cache.MemoryContentCache,
		private val mirrorSwitcher: MirrorSwitcher,
		// Optional: when a runtime extension bundle is loaded, sources it provides override the bundled
		// ones (same name → use the bundle's parser). Null/absent → behaves exactly as the baseline.
		private val extensionManager: org.nekosukuriputo.nekuva.core.extensions.ExtensionManager? = null,
	) {
		private val cache = androidx.collection.ArrayMap<MangaSource, java.lang.ref.WeakReference<MangaRepository>>()

		@Volatile
		private var lastExtGeneration = 0

		@androidx.annotation.AnyThread
		fun create(source: MangaSource): MangaRepository {
			if (source is org.nekosukuriputo.nekuva.core.model.MangaSourceInfo) return create(source.mangaSource)

			// Drop cached parsers when the loaded extension bundle changed (import/update), so the new
			// bundle's parsers take effect without an app restart.
			val gen = extensionManager?.generation ?: 0
			if (gen != lastExtGeneration) {
				synchronized(cache) { cache.clear() }
				lastExtGeneration = gen
			}

			cache[source]?.get()?.let { return it }
			return synchronized(cache) {
				cache[source]?.get()?.let { return it }
				val repository = createRepository(source)
				if (repository != null) {
					cache[source] = java.lang.ref.WeakReference(repository)
					repository
				} else {
					EmptyMangaRepository(source)
				}
			}
		}

		private fun createRepository(source: MangaSource): MangaRepository? = when (source) {
			is org.nekosukuriputo.nekuva.parsers.model.MangaParserSource -> ParserMangaRepository(
				parser = parserFor(source),
				cache = contentCache,
				mirrorSwitcher = mirrorSwitcher,
			)
			else -> null
		}

		// Prefer a loaded extension bundle's parser for this source (override-by-name); fall back to the
		// bundled baseline on any problem so a bad/incompatible bundle can never break source loading.
		private fun parserFor(
			source: org.nekosukuriputo.nekuva.parsers.model.MangaParserSource,
		): org.nekosukuriputo.nekuva.parsers.MangaParser {
			val ext = extensionManager?.loaded
			if (ext != null && ext.sources.any { it.name == source.name }) {
				runCatching {
					return OverrideSourceParser(ext.createParser(source.name, loaderContext), source)
				}
			}
			return loaderContext.newParserInstance(source)
		}
	}
}
