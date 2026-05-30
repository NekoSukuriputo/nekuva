package org.nekosukuriputo.nekuva.core.model

import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.Manga

fun Collection<Manga>.distinctById() = distinctBy { it.id }

val Manga.isLocal: Boolean
    get() = source == LocalMangaSource

val Manga.isBroken: Boolean
    get() = source == UnknownMangaSource

// We use string representation for URL instead of Android Uri
val Manga.appUrl: String
    get() = "nekuva://manga?source=${source.name}&name=$title&url=$url"

fun Manga.chaptersCount(): Int {
    if (chapters.isNullOrEmpty()) {
        return 0
    }
    val counters = mutableMapOf<String?, Int>()
    var max = 0
    chapters?.forEach { x ->
        val c = counters.getOrElse(x.branch) { 0 } + 1
        counters[x.branch] = c
        if (max < c) {
            max = c
        }
    }
    return max
}

fun Manga.isNsfw(): Boolean = contentRating == ContentRating.ADULT || source.isNsfw()
