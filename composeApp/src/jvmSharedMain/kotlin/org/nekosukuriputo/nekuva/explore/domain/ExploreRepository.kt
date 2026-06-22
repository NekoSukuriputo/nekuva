package org.nekosukuriputo.nekuva.explore.domain

import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Doki's "Open random" (ExploreRepository.findRandomManga): pick a random enabled source, fetch a list
 * (biased toward the tags you read most), and return a random manga's details. Simplified vs Doki — drops
 * the tags blacklist (suggestions area) but keeps the popular-tag bias + NSFW exclusion.
 */
class ExploreRepository(
    private val settings: AppSettings,
    private val sourcesRepository: MangaSourcesRepository,
    private val historyRepository: HistoryRepository,
    private val mangaRepositoryFactory: MangaRepository.Factory,
) {

    suspend fun findRandomManga(tagsLimit: Int): Manga {
        val sources = sourcesRepository.getEnabledSources()
        check(sources.isNotEmpty()) { "No sources available" }
        return findRandomManga(tagsLimit) { sources.random() }
    }

    /**
     * Doki's source-screen "Open random" (RemoteListViewModel.openRandom): a random manga from THIS
     * specific source (biased toward your most-read tags), returning its details.
     */
    suspend fun findRandomManga(source: MangaSource, tagsLimit: Int): Manga =
        findRandomManga(tagsLimit) { source }

    private suspend inline fun findRandomManga(tagsLimit: Int, sourcePicker: () -> MangaSource): Manga {
        val tags = runCatchingCancellable { historyRepository.getPopularTags(tagsLimit) }
            .getOrDefault(emptyList()).map { it.title }
        repeat(5) {
            val list = getList(sourcePicker(), tags)
            val manga = list.randomOrNull() ?: return@repeat
            val details = runCatchingCancellable {
                mangaRepositoryFactory.create(manga.source).getDetails(manga)
            }.getOrNull() ?: return@repeat
            if (settings.isSuggestionsExcludeNsfw && details.isNsfw()) return@repeat
            return details
        }
        throw NoSuchElementException()
    }

    private suspend fun getList(source: MangaSource, tags: List<String>): List<Manga> = runCatchingCancellable {
        val repository = mangaRepositoryFactory.create(source)
        val order = repository.sortOrders.randomOrNull() ?: repository.defaultSortOrder
        val availableTags = runCatchingCancellable { repository.getFilterOptions().availableTags }.getOrDefault(emptySet())
        val tag = tags.firstNotNullOfOrNull { title -> availableTags.find { it.title.equals(title, ignoreCase = true) } }
        val list = repository.getList(offset = 0, order = order, filter = MangaListFilter(tags = setOfNotNull(tag))).toMutableList()
        if (settings.isSuggestionsExcludeNsfw) {
            list.removeAll { it.isNsfw() }
        }
        list.shuffle()
        list
    }.getOrDefault(emptyList())
}
