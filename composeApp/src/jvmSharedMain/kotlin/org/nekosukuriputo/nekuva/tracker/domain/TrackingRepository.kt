package org.nekosukuriputo.nekuva.tracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.entity.toManga
import org.nekosukuriputo.nekuva.core.db.entity.toMangaTags
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.tracker.data.MangaWithTrack
import org.nekosukuriputo.nekuva.tracker.data.TrackEntity
import org.nekosukuriputo.nekuva.tracker.data.TrackLogEntity
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaTracking
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaUpdates

private const val MAX_LOG_SIZE = 120

/**
 * Doki's internal "tracker": watches favourited (and optionally history) manga for new chapters and
 * powers the Feed/Updates tab. Ported from Doki's TrackingRepository, simplified for Phase 1 (no
 * branches, no external scrobbling — those are separate areas).
 */
class TrackingRepository(
    private val db: MangaDatabase,
    private val settings: AppSettings,
) {

    private val tracksDao get() = db.getTracksDao()
    private val logsDao get() = db.getTrackLogsDao()

    fun observeUpdatedMangaCount(): Flow<Int> = tracksDao.observeUpdateMangaCount()

    fun observeUnreadUpdatesCount(): Flow<Int> = logsDao.observeUnreadCount()

    fun observeUpdatedManga(limit: Int, filterOptions: Set<ListFilterOption>): Flow<List<MangaTracking>> =
        tracksDao.observeUpdatedManga(limit, filterOptions)
            .map { list -> list.map { it.toTracking() } }
            .distinctUntilChanged()

    suspend fun getTracks(): List<MangaTracking> =
        tracksDao.findAll(offset = 0, limit = Int.MAX_VALUE).map {
            MangaTracking(
                manga = it.manga.toManga(emptySet(), null),
                lastChapterId = it.track.lastChapterId,
                lastChapterDate = it.track.lastChapterDate,
                newChapters = it.track.newChapters,
            )
        }

    suspend fun getTrackOrNull(manga: Manga): MangaTracking? {
        val track = tracksDao.find(manga.id) ?: return null
        return MangaTracking(manga, track.lastChapterId, track.lastChapterDate, track.newChapters)
    }

    suspend fun saveUpdates(updates: MangaUpdates) {
        val current = tracksDao.find(updates.manga.id) ?: TrackEntity.create(updates.manga.id)
        tracksDao.upsert(current.mergeWith(updates))
        if (updates is MangaUpdates.Success && updates.isValid && updates.newChapters.isNotEmpty()) {
            logsDao.insert(
                TrackLogEntity(
                    mangaId = updates.manga.id,
                    chapters = updates.newChapters.joinToString("\n") { it.name ?: it.title ?: "" },
                    createdAt = System.currentTimeMillis(),
                    isUnread = true,
                ),
            )
        }
    }

    suspend fun clearUpdates(ids: Collection<Long>) {
        for (id in ids) tracksDao.clearCounter(id)
    }

    suspend fun clearCounters() = tracksDao.clearCounters()

    suspend fun clearLogs() = logsDao.clear()

    /** Total updates-feed entries (Doki data-cleanup count). */
    suspend fun getLogsCount(): Int = logsDao.count()

    suspend fun gc() {
        tracksDao.gc()
        logsDao.gc()
        logsDao.trim(MAX_LOG_SIZE)
    }

    /** Sync the tracked set from the configured sources (favourites in tracked categories + history). */
    suspend fun updateTracks() {
        tracksDao.gc()
        val ids = tracksDao.findAllIds().toHashSet()
        if (AppSettings.TRACK_HISTORY in settings.trackSources) {
            for (mangaId in db.getHistoryDao().findAllIds()) {
                if (!ids.remove(mangaId)) tracksDao.upsert(TrackEntity.create(mangaId))
            }
        }
        if (AppSettings.TRACK_FAVOURITES in settings.trackSources) {
            for (mangaId in db.getFavouritesDao().findIdsWithTrack()) {
                if (!ids.remove(mangaId)) tracksDao.upsert(TrackEntity.create(mangaId))
            }
        }
        // ids left over are no longer in any tracked source → drop them
        for (mangaId in ids) tracksDao.delete(mangaId)
    }

    private fun MangaWithTrack.toTracking() = MangaTracking(
        manga = manga.toManga(tags.toMangaTags(), null),
        lastChapterId = track.lastChapterId,
        lastChapterDate = track.lastChapterDate,
        newChapters = track.newChapters,
    )

    private fun TrackEntity.mergeWith(updates: MangaUpdates): TrackEntity = when (updates) {
        is MangaUpdates.Failure -> TrackEntity(
            mangaId = mangaId,
            lastChapterId = lastChapterId,
            newChapters = newChapters,
            lastCheckTime = System.currentTimeMillis(),
            lastChapterDate = lastChapterDate,
            lastResult = TrackEntity.RESULT_FAILED,
            lastError = updates.error?.toString(),
        )

        is MangaUpdates.Success -> TrackEntity(
            mangaId = mangaId,
            lastChapterId = updates.lastChapterId().let { if (it == 0L) lastChapterId else it },
            newChapters = if (updates.isValid) newChapters + updates.newChapters.size else 0,
            lastCheckTime = System.currentTimeMillis(),
            lastChapterDate = updates.lastChapterDate().let { if (it == 0L) lastChapterDate else it },
            lastResult = if (updates.isNotEmpty) TrackEntity.RESULT_HAS_UPDATE else TrackEntity.RESULT_NO_UPDATE,
            lastError = null,
        )
    }
}
