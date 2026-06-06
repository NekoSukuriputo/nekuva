package org.nekosukuriputo.nekuva.local.data.output

import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import java.io.File

interface LocalMangaOutput {
    fun close() {}
    suspend fun writeInfo(manga: Manga) {}
    suspend fun writePage(chapter: MangaChapter, page: MangaPage, source: File): Boolean = false
    suspend fun writeChapter(chapter: MangaChapter) {}
    suspend fun writeCover(source: File) {}
    suspend fun deleteChapter(id: Long): Boolean = false

    companion object {
        fun get(root: File, manga: Manga): LocalMangaOutput? = null
    }
}
