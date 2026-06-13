package org.nekosukuriputo.nekuva.tracker.domain.model

import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter

/** Result of checking a manga for new chapters. */
sealed interface MangaUpdates {

    val manga: Manga

    /**
     * @param isValid false on the very first check (no previous baseline) — its [newChapters] is just
     * the baseline and must not be counted as "new"; subsequent checks set it true.
     */
    data class Success(
        override val manga: Manga,
        val newChapters: List<MangaChapter>,
        val isValid: Boolean,
    ) : MangaUpdates {
        val isNotEmpty: Boolean get() = newChapters.isNotEmpty()
        fun lastChapterId(): Long = manga.chapters?.lastOrNull()?.id ?: 0L
        fun lastChapterDate(): Long = manga.chapters?.lastOrNull()?.uploadDate ?: 0L
    }

    data class Failure(
        override val manga: Manga,
        val error: Throwable?,
    ) : MangaUpdates
}
