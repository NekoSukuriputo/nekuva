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
		// Optional: when a runtime extension bundle is loaded, a source it provides whose name matches a
		// bundled source uses the bundle's parser (override). Null/absent → baseline behaviour unchanged.
		private val extensionManager: org.nekosukuriputo.nekuva.core.extensions.ExtensionManager? = null,
	) {
		private val cache = androidx.collection.ArrayMap<MangaSource, java.lang.ref.WeakReference<MangaRepository>>()

		@Volatile
		private var lastExtGeneration = 0

		@androidx.annotation.AnyThread
		fun create(source: MangaSource): MangaRepository {
			if (source is org.nekosukuriputo.nekuva.core.model.MangaSourceInfo) return create(source.mangaSource)

			// Drop cached parsers when the loaded bundle changed (import/update) so it takes effect now.
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

		// Resolve by NAME so a source tagged with the bundle's own enum (different class) still maps to the
		// right parser. Prefer a loaded bundle that overrides a bundled source; otherwise baseline. Any error
		// in the override path falls back to baseline so a bad bundle can't break source loading.
		private fun createRepository(source: MangaSource): MangaRepository? {
			val name = source.name
			val baseline = source as? org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
				?: org.nekosukuriputo.nekuva.parsers.model.MangaParserSource.entries.firstOrNull { it.name == name }

			val ext = extensionManager?.loaded
			if (ext != null && ext.sources.any { it.name == name }) {
				runCatching {
					// Host-side identity: baseline enum if this overrides a bundled source, otherwise the
					// PluginMangaSource (a source the app doesn't ship). The bundle parser still parses.
					val identity: MangaSource = baseline
						?: (source as? org.nekosukuriputo.nekuva.core.model.PluginMangaSource)
						?: org.nekosukuriputo.nekuva.core.model.PluginSourceRegistry.byName(name)
						?: source
					return ParserMangaRepository(
						parser = ext.createParser(name, loaderContext),
						cache = contentCache,
						mirrorSwitcher = mirrorSwitcher,
						sourceOverride = identity,
					)
				}.onFailure {
					// The bundle provides this source but its parser couldn't be instantiated — fall back to
					// the built-in. Make it LOUD (and visible in the About error) so a silent fallback can't
					// hide why a runtime-extension update "doesn't apply" for an overridden source.
					val root = generateSequence(it as Throwable) { e -> e.cause }.last()
					org.nekosukuriputo.nekuva.core.extensions.lastExtensionError =
						"override '$name' failed: ${root::class.simpleName}: ${root.message ?: "(no message)"}"
					println("[Nekuva][ext] ${org.nekosukuriputo.nekuva.core.extensions.lastExtensionError}")
					it.printStackTrace()
				}
			}
			return baseline?.let {
				ParserMangaRepository(
					parser = loaderContext.newParserInstance(it),
					cache = contentCache,
					mirrorSwitcher = mirrorSwitcher,
				)
			}
		}
	}
}
