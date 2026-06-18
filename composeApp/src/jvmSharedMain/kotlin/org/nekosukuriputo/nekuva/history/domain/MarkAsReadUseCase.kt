package org.nekosukuriputo.nekuva.history.domain

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

/**
 * Marks a manga as fully read (Doki `MarkAsReadUseCase` / `action_mark_current`): writes history at the
 * last chapter, last page, percent = 1. Cross-cutting core for History/Favourites selection + Details.
 */
class MarkAsReadUseCase(
    private val historyRepository: HistoryRepository,
    private val mangaRepositoryFactory: MangaRepository.Factory,
) {

    suspend operator fun invoke(manga: Manga) {
        val repo = mangaRepositoryFactory.create(manga.source)
        val details = if (manga.chapters.isNullOrEmpty()) repo.getDetails(manga) else manga
        val lastChapter = checkNotNull(details.chapters).last()
        val pages = repo.getPages(lastChapter)
        historyRepository.addOrUpdate(
            manga = details,
            chapterId = lastChapter.id,
            page = pages.lastIndex.coerceAtLeast(0),
            scroll = 0,
            percent = 1f,
            force = true,
        )
    }

    suspend operator fun invoke(manga: Collection<Manga>) {
        when (manga.size) {
            0 -> Unit
            1 -> invoke(manga.first())
            else -> supervisorScope {
                manga.map { m -> launch { runCatchingCancellable { invoke(m) } } }.joinAll()
            }
        }
    }
}
