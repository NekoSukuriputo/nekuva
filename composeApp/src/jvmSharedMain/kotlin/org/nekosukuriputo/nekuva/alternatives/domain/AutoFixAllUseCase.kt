package org.nekosukuriputo.nekuva.alternatives.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Batch auto-fix (Doki AutoFixService): scan favourites + history for manga whose source is no longer
 * available (removed/unknown), search for the same title in working sources, and migrate each to the best
 * match. Returns the number fixed. Run periodically by the platform worker (Android AutoFixWorker).
 */
class AutoFixAllUseCase(
    private val favouritesRepository: FavouritesRepository,
    private val historyRepository: HistoryRepository,
    private val alternativesUseCase: AlternativesUseCase,
    private val autoFixUseCase: AutoFixUseCase,
) {

    suspend operator fun invoke(): Int {
        val favourites = runCatchingCancellable {
            favouritesRepository.observeAll(ListSortOrder.NEWEST, emptySet(), SCAN_LIMIT).first()
        }.getOrDefault(emptyList())
        val history = runCatchingCancellable { historyRepository.getList(0, SCAN_LIMIT) }.getOrDefault(emptyList())

        val broken = (favourites + history).distinctBy { it.id }.filter { it.isBroken() }
        var fixed = 0
        for (manga in broken) {
            val matches = runCatchingCancellable {
                alternativesUseCase(manga, throughDisabledSources = false).toList()
            }.getOrDefault(emptyList())
            val chosen = runCatchingCancellable { autoFixUseCase(manga, matches) }.getOrNull()
            if (chosen != null) fixed++
        }
        return fixed
    }

    /** Broken = a non-local manga whose source is no longer a known parser source (removed/renamed). */
    private fun Manga.isBroken(): Boolean =
        !isLocal && MangaParserSource.entries.none { it.name == source.name }

    private companion object {
        const val SCAN_LIMIT = 500
    }
}
