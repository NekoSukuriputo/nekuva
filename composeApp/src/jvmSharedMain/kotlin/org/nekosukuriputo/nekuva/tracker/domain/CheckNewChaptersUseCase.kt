package org.nekosukuriputo.nekuva.tracker.domain

import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaUpdates

/**
 * Fetches a manga's current chapter list and computes new chapters relative to the stored baseline.
 * The first check just records the baseline (isValid=false). Simplified vs Doki: ignores branches
 * (compares against the last seen chapter id across the full chapter list).
 */
class CheckNewChaptersUseCase(
    private val trackingRepository: TrackingRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun check(manga: Manga): MangaUpdates = runCatchingCancellable {
        val track = trackingRepository.getTrackOrNull(manga)
        val details = fetchDetails(manga)
        val chapters = details.chapters.orEmpty()
        val baseline = track == null || track.lastChapterId == 0L
        if (baseline || chapters.isEmpty()) {
            MangaUpdates.Success(details, emptyList(), isValid = false)
        } else {
            val lastSeenIndex = chapters.indexOfLast { it.id == track!!.lastChapterId }
            val newChapters = if (lastSeenIndex in 0 until chapters.lastIndex) {
                chapters.subList(lastSeenIndex + 1, chapters.size).toList()
            } else {
                emptyList()
            }
            MangaUpdates.Success(details, newChapters, isValid = true)
        }
    }.getOrElse { MangaUpdates.Failure(manga, it) }

    private suspend fun fetchDetails(manga: Manga): Manga {
        // Use the freshest data we have: parser for remote, local file for downloaded-only manga.
        if (manga.isLocal) {
            return localMangaRepository.getDetails(manga)
        }
        val source = MangaParserSource.entries.find { it.name == manga.source.name }
            ?: return manga
        val details = repositoryFactory.create(source).getDetails(manga)
        runCatching { mangaDataRepository.storeManga(details, replaceExisting = true) }
        return details
    }
}
