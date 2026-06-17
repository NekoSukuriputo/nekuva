package org.nekosukuriputo.nekuva.suggestions.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.entity.toManga
import org.nekosukuriputo.nekuva.core.db.entity.toMangaTags
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.suggestions.data.SuggestionEntity

/** Stored manga suggestions (port of Doki SuggestionRepository). Generation lives in [GenerateSuggestionsUseCase]. */
class SuggestionRepository(
    private val db: MangaDatabase,
    private val mangaDataRepository: MangaDataRepository,
) {

    fun observeAll(): Flow<List<Manga>> = db.getSuggestionDao().observeAll().map { list ->
        list.map { it.manga.toManga(it.tags.toMangaTags(), null) }
    }

    suspend fun isEmpty(): Boolean = db.getSuggestionDao().count() == 0

    suspend fun clear() = db.getSuggestionDao().deleteAll()

    /** Replace all suggestions: persist each manga (so the FK/join resolves) then upsert its suggestion row. */
    suspend fun replace(suggestions: Iterable<MangaSuggestion>) {
        val dao = db.getSuggestionDao()
        dao.deleteAll()
        for ((manga, relevance) in suggestions) {
            mangaDataRepository.storeManga(manga, replaceExisting = false)
            dao.upsert(
                SuggestionEntity(
                    mangaId = manga.id,
                    relevance = relevance,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
