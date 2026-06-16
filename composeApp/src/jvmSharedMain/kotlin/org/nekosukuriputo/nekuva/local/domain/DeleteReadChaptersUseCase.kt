package org.nekosukuriputo.nekuva.local.domain

import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter

/**
 * Deletes already-read chapters from local storage to free space (Doki DeleteReadChaptersUseCase). For each
 * saved manga, keeps the current (per history) chapter onward and deletes everything before it in that branch.
 * Returns the number of chapters deleted.
 */
class DeleteReadChaptersUseCase(
    private val localMangaRepository: LocalMangaRepository,
    private val historyRepository: HistoryRepository,
    private val mangaRepositoryFactory: MangaRepository.Factory,
) {

    suspend operator fun invoke(): Int {
        val list = localMangaRepository.getList(0, null, null)
        if (list.isEmpty()) return 0
        var deleted = 0
        for (manga in list) {
            val task = runCatching { getDeletionTask(LocalManga(manga)) }.getOrNull() ?: continue
            runCatching {
                localMangaRepository.deleteChapters(task.manga.manga, task.chaptersIds)
                deleted += task.chaptersIds.size
            }
        }
        return deleted
    }

    private suspend fun getDeletionTask(manga: LocalManga): DeletionTask? {
        val history = historyRepository.getOne(manga.manga) ?: return null
        val chapters = getAllChapters(manga)
        if (chapters.isEmpty()) return null
        val current = chapters.find { it.id == history.chapterId } ?: return null
        val branch = current.branch
        val filtered = chapters.filter { it.branch == branch }.takeWhile { it.id != history.chapterId }
        if (filtered.isEmpty()) return null
        return DeletionTask(manga, filtered.map { it.id }.toSet())
    }

    private suspend fun getAllChapters(manga: LocalManga): List<MangaChapter> = runCatching {
        val remote = localMangaRepository.getRemoteManga(manga.manga) ?: error("No remote manga")
        mangaRepositoryFactory.create(remote.source).getDetails(remote).chapters ?: emptyList()
    }.recoverCatching {
        manga.manga.chapters?.takeIf { it.isNotEmpty() }
            ?: localMangaRepository.getDetails(manga.manga).chapters ?: emptyList()
    }.getOrDefault(manga.manga.chapters.orEmpty())

    private class DeletionTask(val manga: LocalManga, val chaptersIds: Set<Long>)
}
