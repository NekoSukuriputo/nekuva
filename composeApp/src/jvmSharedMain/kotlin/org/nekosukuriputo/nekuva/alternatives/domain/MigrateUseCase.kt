package org.nekosukuriputo.nekuva.alternatives.domain

import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerManager
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus
import org.nekosukuriputo.nekuva.tracker.data.TrackEntity
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Migrate a manga to a different source (Doki MigrateUseCase): move favourite-category membership, reading
 * history, the tracker row, and scrobbling links from [oldManga] to [newManga], mapping the read position
 * to a chapter in the new source.
 */
class MigrateUseCase(
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val favouritesRepository: FavouritesRepository,
    private val historyRepository: HistoryRepository,
    private val database: MangaDatabase,
    private val scrobblerManager: ScrobblerManager,
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

        // Tracker: re-point the tracking row at the new manga, reset to "externally modified" (Doki).
        val tracksDao = database.getTracksDao()
        val oldTrack = runCatching { tracksDao.find(oldManga.id) }.getOrNull()
        if (oldTrack != null) {
            val lastChapter = newDetails.chapters?.lastOrNull()
            runCatching {
                tracksDao.delete(oldManga.id)
                tracksDao.upsert(
                    TrackEntity(
                        mangaId = newDetails.id,
                        lastChapterId = lastChapter?.id ?: 0L,
                        newChapters = 0,
                        lastCheckTime = System.currentTimeMillis(),
                        lastChapterDate = lastChapter?.uploadDate ?: 0L,
                        lastResult = TrackEntity.RESULT_EXTERNAL_MODIFICATION,
                        lastError = null,
                    ),
                )
            }
        }

        // Scrobbling: move each enabled scrobbler's link from old -> new (Doki MigrateUseCase).
        for (scrobbler in scrobblerManager.scrobblers) {
            if (!scrobbler.isEnabled) continue
            val prev = runCatchingCancellable { scrobbler.getScrobblingInfoOrNull(oldManga.id) }.getOrNull() ?: continue
            runCatchingCancellable {
                scrobbler.unregisterScrobbling(oldManga.id)
                scrobbler.linkManga(newDetails.id, prev.targetId)
                scrobbler.updateScrobblingInfo(
                    mangaId = newDetails.id,
                    rating = prev.rating,
                    status = prev.status ?: when {
                        history == null -> ScrobblingStatus.PLANNED
                        history.percent >= 1f -> ScrobblingStatus.COMPLETED
                        else -> ScrobblingStatus.READING
                    },
                    comment = prev.comment,
                )
            }
        }
    }
}
