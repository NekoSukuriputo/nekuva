package org.nekosukuriputo.nekuva.suggestions.domain

import kotlinx.coroutines.flow.first
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Generates manga suggestions (port of Doki SuggestionsWorker.doWorkImpl, leaner & cross-platform):
 * seed = recent history → most-frequent tags → query a sample of enabled sources by those tags → rank by
 * tag overlap → store. Run on-demand from the suggestions screen (no background worker on Desktop).
 */
class GenerateSuggestionsUseCase(
    private val settings: AppSettings,
    private val historyRepository: HistoryRepository,
    private val favouritesRepository: FavouritesRepository,
    private val sourcesRepository: MangaSourcesRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val suggestionRepository: SuggestionRepository,
) {

    suspend operator fun invoke() {
        if (!settings.isSuggestionsEnabled) {
            suggestionRepository.clear()
            return
        }
        // Seed from recent history + favourites (Doki seeds from both), de-duplicated by manga id.
        val history = runCatchingCancellable { historyRepository.getList(0, SEED_SIZE) }.getOrDefault(emptyList())
        val favourites = runCatchingCancellable {
            favouritesRepository.observeAll(ListSortOrder.NEWEST, emptySet(), SEED_SIZE).first()
        }.getOrDefault(emptyList())
        val seed = (history + favourites).associateBy { it.id }.values.toList()
        if (seed.isEmpty()) return
        val topTags = seed.flatMap { it.tags.orEmpty().map { t -> t.title } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(MAX_TAGS).map { it.key }
        if (topTags.isEmpty()) return

        val excludeNsfw = settings.isSuggestionsExcludeNsfw || settings.isNsfwContentDisabled
        val sources = runCatchingCancellable { sourcesRepository.getEnabledSources() }
            .getOrDefault(emptyList()).shuffled().take(MAX_SOURCES)
        if (sources.isEmpty()) return

        val collected = LinkedHashMap<Long, Manga>()
        for (source in sources) {
            runCatchingCancellable {
                val repo = repositoryFactory.create(source)
                val available = repo.getFilterOptions().availableTags
                val tag = topTags.firstNotNullOfOrNull { title -> available.find { it.title.equals(title, ignoreCase = true) } }
                val list = repo.getList(offset = 0, order = null, filter = MangaListFilter(tags = setOfNotNull(tag)))
                for (m in list) {
                    if (excludeNsfw && m.isNsfw()) continue
                    collected[m.id] = m
                }
            }
            if (collected.size >= MAX_RESULTS) break
        }

        val suggestions = collected.values
            .map { m -> MangaSuggestion(m, computeRelevance(m.tags.orEmpty().map { it.title }, topTags)) }
            .sortedByDescending { it.relevance }
            .take(MAX_RESULTS)
        if (suggestions.isNotEmpty()) suggestionRepository.replace(suggestions)
    }

    private fun computeRelevance(mangaTagTitles: List<String>, topTags: List<String>): Float {
        if (topTags.isEmpty()) return 0f
        var weight = 0
        for (t in mangaTagTitles) {
            val idx = topTags.indexOfFirst { it.equals(t, ignoreCase = true) }
            if (idx >= 0) weight += topTags.size - idx
        }
        val maxWeight = (topTags.size * (topTags.size + 1) / 2).coerceAtLeast(1)
        return (weight.toFloat() / maxWeight).coerceIn(0f, 1f)
    }

    private companion object {
        const val SEED_SIZE = 40
        const val MAX_TAGS = 8
        const val MAX_SOURCES = 8
        const val MAX_RESULTS = 120
    }
}
