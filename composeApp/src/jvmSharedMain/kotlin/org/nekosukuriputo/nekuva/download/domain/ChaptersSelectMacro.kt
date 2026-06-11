package org.nekosukuriputo.nekuva.download.domain

import org.nekosukuriputo.nekuva.parsers.model.MangaChapter

/** Strategies for choosing which chapters to download. Ported from Doki (stdlib collections). */
sealed interface ChaptersSelectMacro {

    /** null = download everything (whole manga). */
    fun getChaptersIds(mangaId: Long, chapters: List<MangaChapter>): Set<Long>?

    data class WholeManga(
        val chaptersCount: Int,
    ) : ChaptersSelectMacro {
        override fun getChaptersIds(mangaId: Long, chapters: List<MangaChapter>): Set<Long>? = null
    }

    data class WholeBranch(
        val branches: Map<String?, Int>,
        val selectedBranch: String?,
    ) : ChaptersSelectMacro {

        val chaptersCount: Int = branches[selectedBranch] ?: 0

        override fun getChaptersIds(mangaId: Long, chapters: List<MangaChapter>): Set<Long> =
            chapters.mapNotNullTo(HashSet()) { c -> if (c.branch == selectedBranch) c.id else null }
    }

    data class FirstChapters(
        val chaptersCount: Int,
        val maxAvailableCount: Int,
        val branch: String?,
    ) : ChaptersSelectMacro {

        override fun getChaptersIds(mangaId: Long, chapters: List<MangaChapter>): Set<Long> {
            val result = LinkedHashSet<Long>(minOf(chaptersCount, chapters.size))
            for (c in chapters) {
                if (c.branch == branch) {
                    result.add(c.id)
                    if (result.size >= chaptersCount) break
                }
            }
            return result
        }
    }

    data class UnreadChapters(
        val chaptersCount: Int,
        val maxAvailableCount: Int,
        val currentChaptersIds: Map<Long, Long>,
    ) : ChaptersSelectMacro {

        override fun getChaptersIds(mangaId: Long, chapters: List<MangaChapter>): Set<Long>? {
            if (chapters.isEmpty()) return null
            val currentChapterId = currentChaptersIds[mangaId] ?: chapters.first().id
            var branch: String? = null
            var isAdding = false
            val result = LinkedHashSet<Long>(minOf(chaptersCount, chapters.size))
            for (c in chapters) {
                if (!isAdding && c.id == currentChapterId) {
                    branch = c.branch
                    isAdding = true
                }
                if (isAdding && c.branch == branch) {
                    result.add(c.id)
                    if (result.size >= chaptersCount) break
                }
            }
            return result
        }
    }
}
