package org.nekosukuriputo.nekuva.alternatives.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

private const val MAX_PARALLELISM = 4

/**
 * "Find similar in other sources" (Doki AlternativesUseCase): searches [manga]'s title across every
 * enabled (or, when [throughDisabledSources], disabled) parser source in parallel and streams each match.
 * Matches are stored so they resolve by id; the reference manga and its own source are excluded.
 */
class AlternativesUseCase(
    private val sourcesRepository: MangaSourcesRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val settings: AppSettings,
) {

    suspend operator fun invoke(manga: Manga, throughDisabledSources: Boolean): Flow<Manga> {
        val sources = getSources(throughDisabledSources).filter { it.name != manga.source.name }
        val skipNsfw = settings.isNsfwContentDisabled
        return channelFlow {
            val semaphore = Semaphore(MAX_PARALLELISM)
            for (source in sources) {
                launch {
                    semaphore.withPermit {
                        val matches = runCatchingCancellable {
                            val repo = repositoryFactory.create(source)
                            if (!repo.filterCapabilities.isSearchSupported) return@withPermit
                            val order = if (SortOrder.RELEVANCE in repo.sortOrders) SortOrder.RELEVANCE else repo.defaultSortOrder
                            repo.getList(0, order, MangaListFilter(query = manga.title))
                        }.getOrNull() ?: return@withPermit
                        val filtered = (if (skipNsfw) matches.filterNot { it.isNsfw() } else matches)
                            .filter { it.id != manga.id }
                        for (m in filtered) {
                            runCatchingCancellable { mangaDataRepository.storeManga(m, replaceExisting = false) }
                            send(m)
                        }
                    }
                }
            }
        }
    }

    private suspend fun getSources(disabled: Boolean): List<MangaSource> = if (disabled) {
        sourcesRepository.getDisabledSources().toList()
    } else {
        sourcesRepository.getEnabledSources()
    }
}
