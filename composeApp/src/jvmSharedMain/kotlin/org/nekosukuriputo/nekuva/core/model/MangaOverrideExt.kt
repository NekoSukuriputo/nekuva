package org.nekosukuriputo.nekuva.core.model

import org.nekosukuriputo.nekuva.parsers.model.Manga

/**
 * Apply a user override (custom title / cover) on top of a parsed [Manga] for display only
 * (Doki `Manga.withOverride`). A blank/absent field falls back to the original source value.
 * The override is stored separately in `MangaPrefsEntity`, never written back into the manga row.
 */
fun Manga.withOverride(override: MangaOverride?): Manga {
    if (override == null) return this
    return copy(
        title = override.title?.takeIf { it.isNotEmpty() } ?: title,
        coverUrl = override.coverUrl?.takeIf { it.isNotEmpty() } ?: coverUrl,
        largeCoverUrl = override.coverUrl?.takeIf { it.isNotEmpty() } ?: largeCoverUrl,
        contentRating = override.contentRating ?: contentRating,
    )
}
