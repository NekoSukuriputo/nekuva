package org.nekosukuriputo.nekuva.search.ui.suggestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.SearchSuggestionType
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.search.domain.MangaSearchRepository

private const val DEBOUNCE_TIMEOUT = 300L
private const val MAX_MANGA_ITEMS = 12
private const val MAX_QUERY_ITEMS = 16
private const val MAX_AUTHORS_ITEMS = 2
private const val MAX_TAGS_ITEMS = 8
private const val MAX_SOURCES_ITEMS = 6
private const val MAX_SOURCES_TIPS_ITEMS = 2

data class SourceSuggestion(val source: MangaSource, val isEnabled: Boolean)

/** Sections of the as-you-type search panel, in Doki's render order. */
data class SearchSuggestionState(
    val tags: List<MangaTag> = emptyList(),
    val manga: List<Manga> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val sources: List<SourceSuggestion> = emptyList(),
    val authors: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tags.isEmpty() && manga.isEmpty() && recentQueries.isEmpty() && sources.isEmpty() && authors.isEmpty()
}

/**
 * As-you-type suggestions for the main search bar (Doki's SearchSuggestionViewModel): recent queries,
 * manga from the DB cache, popular/matching tags, sources, authors — each section gated by the
 * "search suggestion types" preference. Queries are not recorded in incognito mode.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchSuggestionViewModel(
    private val repository: MangaSearchRepository,
    private val settings: AppSettings,
    private val sourcesRepository: MangaSourcesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val invalidationTrigger = MutableStateFlow(0)

    val suggestion: StateFlow<SearchSuggestionState> = combine(
        query.debounce(DEBOUNCE_TIMEOUT),
        invalidationTrigger,
    ) { q, _ -> q }
        .mapLatest { buildSuggestion(it.trim()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchSuggestionState())

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    fun saveQuery(value: String) {
        if (!settings.isIncognitoModeEnabled) {
            repository.saveSearchQuery(value)
            invalidationTrigger.value++
        }
    }

    fun deleteQuery(value: String) {
        repository.deleteSearchQuery(value)
        invalidationTrigger.value++
    }

    fun clearSearchHistory() {
        repository.clearSearchHistory()
        invalidationTrigger.value++
    }

    fun onSourceToggle(source: MangaSource, isEnabled: Boolean) {
        viewModelScope.launch {
            runCatchingCancellable { sourcesRepository.setSourcesEnabled(setOf(source), isEnabled) }
                .onFailure { it.printStackTrace() }
            invalidationTrigger.value++
        }
    }

    private suspend fun buildSuggestion(searchQuery: String): SearchSuggestionState = coroutineScope {
        val types = settings.searchSuggestionTypes
        val tags = async {
            if (SearchSuggestionType.GENRES in types) {
                runCatchingCancellable { repository.getTagsSuggestion(searchQuery, MAX_TAGS_ITEMS, null) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val manga = async {
            if (SearchSuggestionType.MANGA in types) {
                runCatchingCancellable { repository.getMangaSuggestion(searchQuery, MAX_MANGA_ITEMS, null) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val recent = async {
            if (SearchSuggestionType.QUERIES_RECENT in types) {
                runCatchingCancellable { repository.getQuerySuggestion(searchQuery, MAX_QUERY_ITEMS) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val authors = async {
            if (SearchSuggestionType.AUTHORS in types) {
                runCatchingCancellable { repository.getAuthorsSuggestion(searchQuery, MAX_AUTHORS_ITEMS) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        val sources = async {
            val list = if (searchQuery.isEmpty()) {
                // Doki's RECENT_SOURCES tips: most-used sources while the query is still empty.
                if (SearchSuggestionType.RECENT_SOURCES in types) {
                    runCatchingCancellable { repository.getTopSourcesSuggestion(MAX_SOURCES_TIPS_ITEMS) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            } else {
                if (SearchSuggestionType.SOURCES in types) {
                    repository.getSourcesSuggestion(searchQuery, MAX_SOURCES_ITEMS)
                } else {
                    emptyList()
                }
            }
            val enabledNames = runCatchingCancellable {
                sourcesRepository.getEnabledSources().mapTo(HashSet()) { it.name }
            }.getOrDefault(emptySet<String>())
            list.map { SourceSuggestion(it, it.name in enabledNames) }
        }
        SearchSuggestionState(
            tags = tags.await(),
            manga = manga.await(),
            recentQueries = recent.await(),
            sources = sources.await(),
            authors = authors.await(),
        )
    }
}
