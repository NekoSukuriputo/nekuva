package org.nekosukuriputo.nekuva.bookmarks.data

import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.parsers.model.Manga

fun Bookmark.toEntity() = BookmarkEntity(
    mangaId = manga.id,
    pageId = pageId,
    chapterId = chapterId,
    page = page,
    scroll = scroll,
    imageUrl = imageUrl,
    createdAt = createdAt,
    percent = percent,
)

fun BookmarkEntity.toBookmark(manga: Manga) = Bookmark(
    manga = manga,
    pageId = pageId,
    chapterId = chapterId,
    page = page,
    scroll = scroll,
    imageUrl = imageUrl,
    createdAt = createdAt,
    percent = percent,
)

fun List<BookmarkEntity>.toBookmarks(manga: Manga) = map { it.toBookmark(manga) }
