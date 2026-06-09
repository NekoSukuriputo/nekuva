package org.nekosukuriputo.nekuva.remotelist.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.nav.RemoteListRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.model.MangaState
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.SortOrder

class RemoteListViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<RemoteListRoute>()
    val sourceId = route.sourceId

    private val repository: MangaRepository? =
        MangaParserSource.entries.find { it.name == sourceId }?.let { repositoryFactory.create(it) }

    private val _uiState = MutableStateFlow<RemoteListUiState>(RemoteListUiState.Loading)
    val uiState: StateFlow<RemoteListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(FilterUiState())
    val filterState: StateFlow<FilterUiState> = _filterState.asStateFlow()

    private val isTagsExclusionSupported: Boolean = repository?.filterCapabilities?.isTagsExclusionSupported == true

    // Current applied filter selection (the source of truth for queries).
    private var query: String? = null
    private var sortOrder: SortOrder? = repository?.defaultSortOrder
    private var selectedTags: Set<MangaTag> = emptySet()
    private var selectedTagsExclude: Set<MangaTag> = emptySet()
    private var selectedStates: Set<MangaState> = emptySet()
    private var selectedContentRating: Set<ContentRating> = emptySet()
    private var selectedTypes: Set<ContentType> = emptySet()

    // Available options (from getFilterOptions + repository.sortOrders), for the filter sheet.
    private var availableSortOrders: List<SortOrder> = emptyList()
    private var availableTags: List<MangaTag> = emptyList()
    private var availableStates: List<MangaState> = emptyList()
    private var availableContentRating: List<ContentRating> = emptyList()
    private var availableTypes: List<ContentType> = emptyList()

    private var loadingJob: Job? = null

    init {
        // Pre-applied query (e.g. opened from global search "see all").
        route.query?.trim()?.takeIf { it.isNotBlank() }?.let {
            query = it
            _searchQuery.value = it
        }
        availableSortOrders = repository?.sortOrders?.toList().orEmpty()
        emitFilterState(optionsLoading = true)
        loadFilterOptions()
        loadMangaList(append = false)
    }

    private fun loadFilterOptions() {
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                val opts = repo.getFilterOptions()
                availableTags = opts.availableTags.toList()
                availableStates = opts.availableStates.toList()
                availableContentRating = opts.availableContentRating.toList()
                availableTypes = opts.availableContentTypes.toList()
            } catch (_: Exception) {
                // Options unavailable for this source — the sheet shows whatever resolved.
            } finally {
                emitFilterState(optionsLoading = false)
            }
        }
    }

    private fun buildFilter(): MangaListFilter? {
        val filter = MangaListFilter.EMPTY.copy(
            query = query?.takeIf { it.isNotBlank() },
            tags = selectedTags,
            tagsExclude = selectedTagsExclude,
            states = selectedStates,
            contentRating = selectedContentRating,
            types = selectedTypes,
        )
        return filter.takeIf { it.isNotEmpty() }
    }

    private fun isFilterApplied(): Boolean =
        !query.isNullOrBlank() || selectedTags.isNotEmpty() || selectedTagsExclude.isNotEmpty() ||
            selectedStates.isNotEmpty() || selectedContentRating.isNotEmpty() || selectedTypes.isNotEmpty()

    private fun emitFilterState(optionsLoading: Boolean = _filterState.value.isOptionsLoading) {
        _filterState.value = FilterUiState(
            query = query,
            availableSortOrders = availableSortOrders,
            sortOrder = sortOrder,
            availableTags = availableTags,
            selectedTags = selectedTags,
            selectedTagsExclude = selectedTagsExclude,
            isTagsExclusionSupported = isTagsExclusionSupported,
            availableStates = availableStates,
            selectedStates = selectedStates,
            availableContentRating = availableContentRating,
            selectedContentRating = selectedContentRating,
            availableTypes = availableTypes,
            selectedTypes = selectedTypes,
            isOptionsLoading = optionsLoading,
            isFilterApplied = isFilterApplied(),
        )
    }

    private fun loadMangaList(append: Boolean) {
        val repo = repository
        if (repo == null) {
            _uiState.value = RemoteListUiState.Error(IllegalArgumentException("Source not found: $sourceId"))
            return
        }
        if (append && (_uiState.value as? RemoteListUiState.Success)?.isAppending == true) return

        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            val currentState = _uiState.value
            val prevList = if (append && currentState is RemoteListUiState.Success) currentState.mangaList else emptyList()

            if (append && currentState is RemoteListUiState.Success) {
                _uiState.value = currentState.copy(isAppending = true)
            } else {
                _uiState.value = RemoteListUiState.Loading
            }

            try {
                val list = repo.getList(
                    offset = prevList.size,
                    order = sortOrder,
                    filter = buildFilter(),
                )
                if (!append && list.isEmpty()) {
                    _uiState.value = RemoteListUiState.Empty(canResetFilter = isFilterApplied())
                } else {
                    list.forEach { manga -> mangaDataRepository.storeManga(manga, replaceExisting = false) }
                    val newList = (prevList + list).distinctBy { it.id }
                    _uiState.value = RemoteListUiState.Success(
                        mangaList = newList,
                        isAppending = false,
                        hasNextPage = list.isNotEmpty() && newList.size > prevList.size,
                    )
                }
            } catch (e: Exception) {
                if (append && currentState is RemoteListUiState.Success) {
                    _uiState.value = currentState.copy(isAppending = false, hasNextPage = false)
                } else {
                    _uiState.value = RemoteListUiState.Error(e)
                }
            }
        }
    }

    /** Reload from offset 0 with the current filter selection. */
    private fun reload() {
        emitFilterState()
        loadMangaList(append = false)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state is RemoteListUiState.Success && state.hasNextPage && !state.isAppending) {
            loadMangaList(append = true)
        }
    }

    fun retry() = loadMangaList(append = false)

    // --- Search ---

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    /** Submit the search field (IME action). */
    fun submitSearch() {
        query = _searchQuery.value.trim().takeIf { it.isNotBlank() }
        reload()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        if (query != null) {
            query = null
            reload()
        }
    }

    // --- Filter (LIVE — like Doki: every change re-queries immediately, no "Apply") ---

    fun setSortOrder(value: SortOrder) {
        sortOrder = value
        reload()
    }

    /** Live toggle of an include genre (quick-filter chip row + sheet). */
    fun toggleTag(tag: MangaTag) {
        selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
        selectedTagsExclude = selectedTagsExclude - tag
        reload()
    }

    fun toggleTagExclude(tag: MangaTag) {
        selectedTagsExclude = if (tag in selectedTagsExclude) selectedTagsExclude - tag else selectedTagsExclude + tag
        selectedTags = selectedTags - tag
        reload()
    }

    fun toggleState(state: MangaState) {
        selectedStates = if (state in selectedStates) selectedStates - state else selectedStates + state
        reload()
    }

    fun toggleContentRating(rating: ContentRating) {
        selectedContentRating = if (rating in selectedContentRating) selectedContentRating - rating else selectedContentRating + rating
        reload()
    }

    fun toggleType(type: ContentType) {
        selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
        reload()
    }

    fun resetFilter() {
        query = null
        _searchQuery.value = ""
        sortOrder = repository?.defaultSortOrder
        selectedTags = emptySet()
        selectedTagsExclude = emptySet()
        selectedStates = emptySet()
        selectedContentRating = emptySet()
        selectedTypes = emptySet()
        reload()
    }
}

data class FilterUiState(
    val query: String? = null,
    val availableSortOrders: List<SortOrder> = emptyList(),
    val sortOrder: SortOrder? = null,
    val availableTags: List<MangaTag> = emptyList(),
    val selectedTags: Set<MangaTag> = emptySet(),
    val selectedTagsExclude: Set<MangaTag> = emptySet(),
    val isTagsExclusionSupported: Boolean = false,
    val availableStates: List<MangaState> = emptyList(),
    val selectedStates: Set<MangaState> = emptySet(),
    val availableContentRating: List<ContentRating> = emptyList(),
    val selectedContentRating: Set<ContentRating> = emptySet(),
    val availableTypes: List<ContentType> = emptyList(),
    val selectedTypes: Set<ContentType> = emptySet(),
    val isOptionsLoading: Boolean = false,
    val isFilterApplied: Boolean = false,
)

sealed interface RemoteListUiState {
    data object Loading : RemoteListUiState
    data class Empty(val canResetFilter: Boolean = false) : RemoteListUiState
    data class Success(
        val mangaList: List<Manga>,
        val isAppending: Boolean = false,
        val hasNextPage: Boolean = true,
    ) : RemoteListUiState
    data class Error(val exception: Throwable) : RemoteListUiState
}
