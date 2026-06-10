package org.nekosukuriputo.nekuva.bookmarks.domain

import org.nekosukuriputo.nekuva.parsers.model.Manga

/** A bookmarked page within a manga chapter (mirrors Doki's Bookmark). */
data class Bookmark(
    val manga: Manga,
    val pageId: Long,
    val chapterId: Long,
    val page: Int,
    val scroll: Int,
    val imageUrl: String,
    val createdAt: Long,
    val percent: Float,
)
