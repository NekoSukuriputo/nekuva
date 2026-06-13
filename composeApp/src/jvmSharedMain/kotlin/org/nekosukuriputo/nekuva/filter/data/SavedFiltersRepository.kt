package org.nekosukuriputo.nekuva.filter.data

import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.nekosukuriputo.nekuva.core.model.MangaSource
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.Demographic
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaState
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.YEAR_UNKNOWN
import java.util.Locale

/**
 * A named, per-source filter preset (Doki's "Saved filters"). The filter itself is stored as the
 * serializable [FilterSnapshot] DTO; [id] is derived from the name, like Doki.
 */
@Serializable
data class PersistableFilter(
    @SerialName("name") val name: String,
    @SerialName("source") val sourceName: String,
    @SerialName("filter") val snapshot: FilterSnapshot,
) {
    val id: Int get() = name.hashCode()

    companion object {
        const val MAX_TITLE_LENGTH = 18
    }
}

/** Serializable mirror of [MangaListFilter] (the exts model is not @Serializable). */
@Serializable
data class FilterSnapshot(
    @SerialName("query") val query: String? = null,
    @SerialName("tags") val tags: List<TagDto> = emptyList(),
    @SerialName("tagsExclude") val tagsExclude: List<TagDto> = emptyList(),
    @SerialName("locale") val locale: String? = null,
    @SerialName("originalLocale") val originalLocale: String? = null,
    @SerialName("states") val states: List<String> = emptyList(),
    @SerialName("contentRating") val contentRating: List<String> = emptyList(),
    @SerialName("types") val types: List<String> = emptyList(),
    @SerialName("demographics") val demographics: List<String> = emptyList(),
    @SerialName("year") val year: Int = YEAR_UNKNOWN,
    @SerialName("yearFrom") val yearFrom: Int = YEAR_UNKNOWN,
    @SerialName("yearTo") val yearTo: Int = YEAR_UNKNOWN,
    @SerialName("author") val author: String? = null,
) {

    @Serializable
    data class TagDto(@SerialName("key") val key: String, @SerialName("title") val title: String)

    fun toFilter(sourceName: String): MangaListFilter {
        val source = MangaSource(sourceName)
        return MangaListFilter.EMPTY.copy(
            query = query,
            tags = tags.mapTo(LinkedHashSet()) { MangaTag(title = it.title, key = it.key, source = source) },
            tagsExclude = tagsExclude.mapTo(LinkedHashSet()) { MangaTag(title = it.title, key = it.key, source = source) },
            locale = locale?.let(Locale::forLanguageTag),
            originalLocale = originalLocale?.let(Locale::forLanguageTag),
            states = states.mapNotNullTo(LinkedHashSet()) { v -> MangaState.entries.find { it.name == v } },
            contentRating = contentRating.mapNotNullTo(LinkedHashSet()) { v -> ContentRating.entries.find { it.name == v } },
            types = types.mapNotNullTo(LinkedHashSet()) { v -> ContentType.entries.find { it.name == v } },
            demographics = demographics.mapNotNullTo(LinkedHashSet()) { v -> Demographic.entries.find { it.name == v } },
            year = year,
            yearFrom = yearFrom,
            yearTo = yearTo,
            author = author,
        )
    }

    companion object {
        fun of(filter: MangaListFilter): FilterSnapshot = FilterSnapshot(
            query = filter.query,
            tags = filter.tags.map { TagDto(it.key, it.title) },
            tagsExclude = filter.tagsExclude.map { TagDto(it.key, it.title) },
            locale = filter.locale?.toLanguageTag(),
            originalLocale = filter.originalLocale?.toLanguageTag(),
            states = filter.states.map { it.name },
            contentRating = filter.contentRating.map { it.name },
            types = filter.types.map { it.name },
            demographics = filter.demographics.map { it.name },
            year = filter.year,
            yearFrom = filter.yearFrom,
            yearTo = filter.yearTo,
            author = filter.author,
        )
    }
}

/**
 * Stores named filter presets per source in the app settings as JSON (Doki keeps them in per-source
 * SharedPreferences; the KMP equivalent is the shared [ObservableSettings] with a per-source key
 * prefix). Mutations bump an in-memory version so [observeAll] re-reads — only this class writes
 * these keys.
 */
class SavedFiltersRepository(
    private val settings: ObservableSettings,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val version = MutableStateFlow(0)

    fun observeAll(sourceName: String): Flow<List<PersistableFilter>> =
        version.map { getAll(sourceName) }

    fun getAll(sourceName: String): List<PersistableFilter> {
        val prefix = prefix(sourceName)
        return settings.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { key -> decode(settings.getStringOrNull(key)) }
            .sortedBy { it.name }
    }

    fun save(sourceName: String, name: String, filter: MangaListFilter): PersistableFilter {
        val persistable = PersistableFilter(
            name = name,
            sourceName = sourceName,
            snapshot = FilterSnapshot.of(filter),
        )
        settings.putString(key(sourceName, persistable.id), json.encodeToString(persistable))
        version.value++
        return persistable
    }

    fun rename(sourceName: String, id: Int, newName: String) {
        val old = decode(settings.getStringOrNull(key(sourceName, id))) ?: return
        val renamed = old.copy(name = newName)
        settings.remove(key(sourceName, id))
        settings.putString(key(sourceName, renamed.id), json.encodeToString(renamed))
        version.value++
    }

    fun delete(sourceName: String, id: Int) {
        settings.remove(key(sourceName, id))
        version.value++
    }

    private fun decode(raw: String?): PersistableFilter? = raw?.let {
        try {
            json.decodeFromString<PersistableFilter>(it)
        } catch (e: Exception) {
            e.printStackTraceDebug()
            null
        }
    }

    private companion object {
        fun prefix(sourceName: String) = "__pf_${sourceName}_"
        fun key(sourceName: String, id: Int) = prefix(sourceName) + id
    }
}
