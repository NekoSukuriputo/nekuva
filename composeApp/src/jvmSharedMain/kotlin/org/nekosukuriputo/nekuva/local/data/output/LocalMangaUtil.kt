package org.nekosukuriputo.nekuva.local.data.output

import org.nekosukuriputo.nekuva.parsers.model.Manga

class LocalMangaUtil(private val manga: Manga) {
    suspend fun deleteChapters(ids: Set<Long>) {}
}
