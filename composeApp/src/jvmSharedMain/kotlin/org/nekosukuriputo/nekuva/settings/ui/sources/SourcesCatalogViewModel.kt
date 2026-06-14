package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.SourcesSortOrder
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

/** Filter applied to the sources catalog (Doki SourcesCatalogFilter). */
data class SourcesCatalogFilter(
    val types: Set<ContentType> = emptySet(),
    val locale: String? = null,
    val isNewOnly: Boolean = false,
)

/** Add-new-source catalog (Doki SourcesCatalogViewModel): browse disabled sources, search + filter, add. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourcesCatalogViewModel(
    private val repository: MangaSourcesRepository,
    private val settings: AppSettings,
) : ViewModel() {

    /** Available locales (null = all), sorted; null first. */
    val locales: List<String?> = buildList {
        add(null)
        addAll(repository.allMangaSources.mapNotNull { it.locale }.distinct().sorted())
    }

    /** Content types present across sources (NSFW types hidden when NSFW is disabled). */
    val contentTypes: List<ContentType> = repository.allMangaSources
        .map { it.contentType }
        .distinct()
        .let { types -> if (settings.isNsfwContentDisabled) types.filterNot { it == ContentType.HENTAI } else types }

    private val searchQuery = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)
    val filter = MutableStateFlow(
        SourcesCatalogFilter(
            locale = java.util.Locale.getDefault().language.takeIf { lang -> lang in repository.allMangaSources.map { it.locale } },
        ),
    )

    val content: StateFlow<List<MangaParserSource>> =
        combine(searchQuery, filter, refreshTrigger) { query, f, _ -> query to f }
            .mapLatest { (query, f) ->
                repository.queryParserSources(
                    isDisabledOnly = true,
                    isNewOnly = f.isNewOnly,
                    excludeBroken = false,
                    types = f.types,
                    query = query,
                    locale = f.locale,
                    sortOrder = SourcesSortOrder.ALPHABETIC,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun performSearch(query: String?) {
        searchQuery.value = query?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setLocale(value: String?) {
        filter.value = filter.value.copy(locale = value)
    }

    fun setContentType(type: ContentType, isAdd: Boolean) {
        val types = filter.value.types.toMutableSet()
        if (isAdd) types.add(type) else types.remove(type)
        filter.value = filter.value.copy(types = types)
    }

    fun setNewOnly(value: Boolean) {
        filter.value = filter.value.copy(isNewOnly = value)
    }

    fun addSource(source: MangaSource) {
        viewModelScope.launch {
            repository.setSourcesEnabled(setOf(source), true)
            refreshTrigger.value++
        }
    }
}
