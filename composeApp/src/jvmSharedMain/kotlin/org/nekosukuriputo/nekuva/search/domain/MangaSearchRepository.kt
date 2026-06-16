package org.nekosukuriputo.nekuva.search.domain

import com.russhwolf.settings.ObservableSettings
import kotlinx.serialization.json.Json
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.entity.toMangaTagsList
import org.nekosukuriputo.nekuva.core.db.entity.toManga
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.util.levenshteinDistance

/**
 * Suggestion sources for the search bar (Doki's MangaSearchRepository). Recent queries are kept as a
 * JSON list in [ObservableSettings] (most-recent first) — the KMP stand-in for Android's
 * SearchRecentSuggestions provider. Manga/tags/authors come from the local DB cache.
 */
class MangaSearchRepository(
    private val db: MangaDatabase,
    private val sourcesRepository: MangaSourcesRepository,
    private val prefs: ObservableSettings,
    private val settings: AppSettings,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getMangaSuggestion(query: String, limit: Int, source: MangaSource?): List<Manga> = when {
        // Doki seeds the empty query from the suggestions table; that area is not migrated yet.
        query.isEmpty() -> emptyList()
        source != null -> db.getMangaDao().searchByTitle("%$query%", source.name, limit)
        else -> db.getMangaDao().searchByTitle("%$query%", limit)
    }.let {
        if (settings.isNsfwContentDisabled) it.filterNot { x -> x.manga.isNsfw } else it
    }.map {
        it.toManga()
    }.sortedBy { x ->
        x.title.levenshteinDistance(query)
    }

    suspend fun getTagsSuggestion(query: String, limit: Int, source: MangaSource?): List<MangaTag> = when {
        query.isNotEmpty() && source != null -> db.getTagsDao().findTags(source.name, "%$query%", limit)
        query.isNotEmpty() -> db.getTagsDao().findTags("%$query%", limit)
        source != null -> db.getTagsDao().findPopularTags(source.name, limit)
        else -> db.getTagsDao().findPopularTags(limit)
    }.toMangaTagsList()

    suspend fun getAuthorsSuggestion(query: String, limit: Int): List<String> =
        if (query.isEmpty()) emptyList() else db.getMangaDao().findAuthors("$query%", limit)

    fun getSourcesSuggestion(query: String, limit: Int): List<MangaSource> {
        if (query.length < 3) {
            return emptyList()
        }
        val skipNsfw = settings.isNsfwContentDisabled
        return sourcesRepository.allMangaSources
            .filter { x ->
                (x.contentType != ContentType.HENTAI || !skipNsfw) && x.title.contains(query, ignoreCase = true)
            }
            .take(limit)
    }

    suspend fun getTopSourcesSuggestion(limit: Int): List<MangaSource> = sourcesRepository.getTopSources(limit)

    // --- Recent search queries ---

    fun getQuerySuggestion(query: String, limit: Int): List<String> = loadHistory()
        .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
        .take(limit)

    fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val history = ArrayDeque(loadHistory())
        history.removeAll { it.equals(trimmed, ignoreCase = true) }
        history.addFirst(trimmed)
        while (history.size > MAX_HISTORY) {
            history.removeLast()
        }
        persistHistory(history)
    }

    fun deleteSearchQuery(query: String) {
        persistHistory(loadHistory().filterNot { it.equals(query, ignoreCase = true) })
    }

    fun clearSearchHistory() {
        prefs.remove(KEY_SEARCH_HISTORY)
    }

    /** Number of stored search-history queries (Doki data-cleanup count). */
    fun getSearchHistoryCount(): Int = loadHistory().size

    private fun loadHistory(): List<String> = prefs.getStringOrNull(KEY_SEARCH_HISTORY)?.let {
        try {
            json.decodeFromString<List<String>>(it)
        } catch (_: Exception) {
            null
        }
    }.orEmpty()

    private fun persistHistory(history: Collection<String>) {
        prefs.putString(KEY_SEARCH_HISTORY, json.encodeToString(history.toList()))
    }

    private companion object {
        const val KEY_SEARCH_HISTORY = "search_history"
        const val MAX_HISTORY = 50
    }
}
