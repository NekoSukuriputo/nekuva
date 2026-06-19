package org.nekosukuriputo.nekuva.alternatives.domain

import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Migrate a manga to a different source (Doki MigrateUseCase): move favourite-category membership and
 * reading history from [oldManga] to [newManga], mapping the read position to a chapter in the new source.
 * Tracks + scrobbling migration are deferred (recorded in MIGRATION.md) — tracks re-resolve on next check.
 */
class MigrateUseCase(
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val favouritesRepository: FavouritesRepository,
    private val historyRepository: HistoryRepository,
) {

    suspend operator fun invoke(oldManga: Manga, newManga: Manga) {
        val newDetails = if (newManga.chapters.isNullOrEmpty()) {
            runCatchingCancellable { repositoryFactory.create(newManga.source).getDetails(newManga) }
                .getOrDefault(newManga)
        } else {
            newManga
        }
        mangaDataRepository.storeManga(newDetails, replaceExisting = true)

        // Favourites: move every category membership from old -> new, then drop the old manga.
        val categories = favouritesRepository.getCategoriesIds(oldManga.id)
        if (categories.isNotEmpty()) {
            categories.forEach { categoryId -> favouritesRepository.addToCategory(categoryId, listOf(newDetails)) }
            favouritesRepository.removeFromFavourites(listOf(oldManga.id))
        }

        // History: map the read percent proportionally to a chapter in the new source, then drop the old row.
        val history = historyRepository.getOne(oldManga)
        if (history != null) {
            val chapters = newDetails.chapters.orEmpty()
            if (chapters.isNotEmpty()) {
                val index = (chapters.lastIndex * history.percent).toInt().coerceIn(0, chapters.lastIndex)
                historyRepository.addOrUpdate(
                    manga = newDetails,
                    chapterId = chapters[index].id,
                    page = history.page,
                    scroll = history.scroll,
                    percent = history.percent,
                    force = true,
                )
            }
            historyRepository.delete(oldManga)
        }
    }
}
