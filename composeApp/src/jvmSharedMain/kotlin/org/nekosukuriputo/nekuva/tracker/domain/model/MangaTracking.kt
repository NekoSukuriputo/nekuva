package org.nekosukuriputo.nekuva.tracker.domain.model

import org.nekosukuriputo.nekuva.parsers.model.Manga

/** A tracked manga's state: the last seen chapter + how many new chapters are unread. */
data class MangaTracking(
    val manga: Manga,
    val lastChapterId: Long,
    val lastChapterDate: Long,
    val newChapters: Int,
)
