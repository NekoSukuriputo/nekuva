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
import org.nekosukuriputo.nekuva.filter.data.FilterSnapshot
import org.nekosukuriputo.nekuva.filter.data.PersistableFilter
import org.nekosukuriputo.nekuva.filter.data.SavedFiltersRepository
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.Demographic
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.model.MangaState
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.model.YEAR_UNKNOWN
import java.util.Locale

class RemoteListViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val savedFiltersRepository: SavedFiltersRepository,
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

    // Source capabilities (gate which filter fields are shown — Doki's FilterCoordinator.capabilities).
    private val capabilities = repository?.filterCapabilities
    private val isTagsExclusionSupported: Boolean = capabilities?.isTagsExclusionSupported == true
    private val isMultipleTagsSupported: Boolean = capabilities?.isMultipleTagsSupported != false
    private val isSearchWithFiltersSupported: Boolean = capabilities?.isSearchWithFiltersSupported == true
    private val isYearSupported: Boolean = capabilities?.isYearSupported == true
    private val isYearRangeSupported: Boolean = capabilities?.isYearRangeSupported == true
    private val isOriginalLocaleSupported: Boolean = capabilities?.isOriginalLocaleSupported == true
    private val isAuthorSearchSupported: Boolean = capabilities?.isAuthorSearchSupported == true

    // Current applied filter selection (the source of truth for queries).
    private var query: String? = null
    private var sortOrder: SortOrder? = repository?.defaultSortOrder
    private var selectedTags: Set<MangaTag> = emptySet()
    private var selectedTagsExclude: Set<MangaTag> = emptySet()
    private var selectedStates: Set<MangaState> = emptySet()
    private var selectedContentRating: Set<ContentRating> = emptySet()
    private var selectedTypes: Set<ContentType> = emptySet()
    private var selectedDemographics: Set<Demographic> = emptySet()
    private var selectedLocale: Locale? = null
    private var selectedOriginalLocale: Locale? = null
    private var selectedYear: Int = YEAR_UNKNOWN
    private var selectedYearFrom: Int = YEAR_UNKNOWN
    private var selectedYearTo: Int = YEAR_UNKNOWN
    private var author: String? = null

    // Available options (from getFilterOptions + repository.sortOrders), for the filter sheet.
    private var availableSortOrders: List<SortOrder> = emptyList()
    private var availableTags: List<MangaTag> = emptyList()
    private var availableStates: List<MangaState> = emptyList()
    private var availableContentRating: List<ContentRating> = emptyList()
    private var availableTypes: List<ContentType> = emptyList()
    private var availableDemographics: List<Demographic> = emptyList()
    private var availableLocales: List<Locale> = emptyList()
    private var catalogTags: List<MangaTag> = emptyList()
    private var savedFilters: List<PersistableFilter> = emptyList()

    private var loadingJob: Job? = null

    init {
        // Pre-applied query (e.g. opened from global search "see all").
        route.query?.trim()?.takeIf { it.isNotBlank() }?.let {
            query = it
            _searchQuery.value = it
        }
        // Pre-applied genre filter (Doki openList(tag)): a tag chip in Details → this source filtered by tag.
        val routeSource = repository?.source
        if (routeSource != null && !route.tagKey.isNullOrBlank() && !route.tagTitle.isNullOrBlank()) {
            selectedTags = setOf(MangaTag(title = route.tagTitle!!, key = route.tagKey!!, source = routeSource))
        }
        // Pre-applied author filter (Doki openList(source, MangaListFilter(author=…))).
        route.author?.trim()?.takeIf { it.isNotBlank() }?.let { author = it }
        availableSortOrders = repository?.sortOrders?.toList().orEmpty()
        emitFilterState(optionsLoading = true)
        loadFilterOptions()
        loadMangaList(append = false)
        viewModelScope.launch {
            savedFiltersRepository.observeAll(sourceId).collect {
                savedFilters = it
                emitFilterState()
            }
        }
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
                availableDemographics = opts.availableDemographics.toList()
                availableLocales = opts.availableLocales.toList()
            } catch (_: Exception) {
                // Options unavailable for this source — the sheet shows whatever resolved.
            }
            // Full tags catalog (Doki TagsCatalogViewModel.buildList): source options + tags already
            // cached in the DB, deduplicated by title, title-sorted.
            try {
                val dbTags = mangaDataRepository.findTags(repo.source)
                val seen = HashSet<String>(availableTags.size + dbTags.size)
                catalogTags = (availableTags + dbTags)
                    .filter { seen.add(it.title) }
                    .sortedBy { it.title.lowercase() }
            } catch (_: Exception) {
                catalogTags = availableTags.sortedBy { it.title.lowercase() }
            }
            emitFilterState(optionsLoading = false)
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
            demographics = selectedDemographics,
            locale = selectedLocale,
            originalLocale = selectedOriginalLocale,
            year = selectedYear,
            yearFrom = selectedYearFrom,
            yearTo = selectedYearTo,
            author = author,
        )
        return filter.takeIf { it.isNotEmpty() }
    }

    private fun hasNonSearchSelection(): Boolean =
        selectedTags.isNotEmpty() || selectedTagsExclude.isNotEmpty() ||
            selectedStates.isNotEmpty() || selectedContentRating.isNotEmpty() ||
            selectedTypes.isNotEmpty() || selectedDemographics.isNotEmpty() ||
            selectedLocale != null || selectedOriginalLocale != null ||
            selectedYear != YEAR_UNKNOWN || selectedYearFrom != YEAR_UNKNOWN ||
            selectedYearTo != YEAR_UNKNOWN || !author.isNullOrBlank()

    private fun isFilterApplied(): Boolean = !query.isNullOrBlank() || hasNonSearchSelection()

    /**
     * Doki's `takeQueryIfSupported`: if the source cannot combine a text query with other filters,
     * applying any non-search filter drops the active query (and vice versa in [submitSearch]).
     */
    private fun dropQueryIfUnsupported() {
        if (!isSearchWithFiltersSupported && !query.isNullOrBlank() && hasNonSearchSelection()) {
            query = null
            _searchQuery.value = ""
        }
    }

    private fun emitFilterState(optionsLoading: Boolean = _filterState.value.isOptionsLoading) {
        val currentSnapshot = FilterSnapshot.of(buildFilter() ?: MangaListFilter.EMPTY)
        _filterState.value = FilterUiState(
            query = query,
            savedFilters = savedFilters,
            selectedSavedFilterId = savedFilters.find { it.snapshot == currentSnapshot }?.id,
            availableSortOrders = availableSortOrders,
            sortOrder = sortOrder,
            availableTags = availableTags,
            catalogTags = catalogTags,
            selectedTags = selectedTags,
            selectedTagsExclude = selectedTagsExclude,
            isTagsExclusionSupported = isTagsExclusionSupported,
            availableStates = availableStates,
            selectedStates = selectedStates,
            availableContentRating = availableContentRating,
            selectedContentRating = selectedContentRating,
            availableTypes = availableTypes,
            selectedTypes = selectedTypes,
            availableDemographics = availableDemographics,
            selectedDemographics = selectedDemographics,
            availableLocales = availableLocales,
            selectedLocale = selectedLocale,
            selectedOriginalLocale = selectedOriginalLocale,
            isOriginalLocaleSupported = isOriginalLocaleSupported,
            isYearSupported = isYearSupported,
            isYearRangeSupported = isYearRangeSupported,
            selectedYear = selectedYear,
            selectedYearFrom = selectedYearFrom,
            selectedYearTo = selectedYearTo,
            isAuthorSearchSupported = isAuthorSearchSupported,
            author = author,
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
        dropQueryIfUnsupported()
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
        // Doki: a source that cannot search WITH filters resets the other filters on a new query.
        if (!isSearchWithFiltersSupported && query != null) {
            clearSelections()
        }
        emitFilterState()
        loadMangaList(append = false)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        if (query != null) {
            query = null
            emitFilterState()
            loadMangaList(append = false)
        }
    }

    // --- Filter (LIVE — like Doki: every change re-queries immediately, no "Apply") ---

    fun setSortOrder(value: SortOrder) {
        sortOrder = value
        // Doki persists the chosen order as the source's default.
        repository?.defaultSortOrder = value
        emitFilterState()
        loadMangaList(append = false)
    }

    /** Live toggle of an include genre (quick-filter chip row + sheet). */
    fun toggleTag(tag: MangaTag) {
        selectedTags = when {
            tag in selectedTags -> selectedTags - tag
            isMultipleTagsSupported -> selectedTags + tag
            else -> setOf(tag) // single-tag sources replace the selection
        }
        selectedTagsExclude = selectedTagsExclude - selectedTags
        reload()
    }

    fun toggleTagExclude(tag: MangaTag) {
        selectedTagsExclude = when {
            tag in selectedTagsExclude -> selectedTagsExclude - tag
            isMultipleTagsSupported -> selectedTagsExclude + tag
            else -> setOf(tag)
        }
        selectedTags = selectedTags - selectedTagsExclude
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

    fun toggleDemographic(demographic: Demographic) {
        selectedDemographics =
            if (demographic in selectedDemographics) selectedDemographics - demographic else selectedDemographics + demographic
        reload()
    }

    fun setLocale(value: Locale?) {
        selectedLocale = value
        reload()
    }

    fun setOriginalLocale(value: Locale?) {
        selectedOriginalLocale = value
        reload()
    }

    /** [YEAR_UNKNOWN] clears the year filter (slider dragged to its minimum, like Doki). */
    fun setYear(value: Int) {
        selectedYear = value
        reload()
    }

    fun setYearRange(from: Int, to: Int) {
        selectedYearFrom = from
        selectedYearTo = to
        reload()
    }

    fun setAuthor(value: String?) {
        author = value?.trim()?.takeIf { it.isNotBlank() }
        reload()
    }

    private fun clearSelections() {
        selectedTags = emptySet()
        selectedTagsExclude = emptySet()
        selectedStates = emptySet()
        selectedContentRating = emptySet()
        selectedTypes = emptySet()
        selectedDemographics = emptySet()
        selectedLocale = null
        selectedOriginalLocale = null
        selectedYear = YEAR_UNKNOWN
        selectedYearFrom = YEAR_UNKNOWN
        selectedYearTo = YEAR_UNKNOWN
        author = null
    }

    fun resetFilter() {
        query = null
        _searchQuery.value = ""
        sortOrder = repository?.defaultSortOrder
        clearSelections()
        emitFilterState()
        loadMangaList(append = false)
    }

    // --- Saved filters (Doki presets: save / apply / rename / delete) ---

    fun saveCurrentFilter(name: String) {
        val filter = buildFilter() ?: return
        savedFiltersRepository.save(sourceId, name.trim().take(PersistableFilter.MAX_TITLE_LENGTH), filter)
    }

    fun renameSavedFilter(id: Int, newName: String) {
        savedFiltersRepository.rename(sourceId, id, newName.trim().take(PersistableFilter.MAX_TITLE_LENGTH))
    }

    fun deleteSavedFilter(id: Int) {
        savedFiltersRepository.delete(sourceId, id)
    }

    /** Apply a saved preset; tapping the currently-applied one clears the filter (Doki toggle). */
    fun toggleSavedFilter(id: Int) {
        val preset = savedFilters.find { it.id == id } ?: return
        if (_filterState.value.selectedSavedFilterId == id) {
            resetFilter()
            return
        }
        val filter = preset.snapshot.toFilter(sourceId)
        query = filter.query
        _searchQuery.value = filter.query.orEmpty()
        selectedTags = filter.tags
        selectedTagsExclude = filter.tagsExclude
        selectedStates = filter.states
        selectedContentRating = filter.contentRating
        selectedTypes = filter.types
        selectedDemographics = filter.demographics
        selectedLocale = filter.locale
        selectedOriginalLocale = filter.originalLocale
        selectedYear = filter.year
        selectedYearFrom = filter.yearFrom
        selectedYearTo = filter.yearTo
        author = filter.author
        emitFilterState()
        loadMangaList(append = false)
    }
}

data class FilterUiState(
    val query: String? = null,
    val savedFilters: List<PersistableFilter> = emptyList(),
    val selectedSavedFilterId: Int? = null,
    val availableSortOrders: List<SortOrder> = emptyList(),
    val sortOrder: SortOrder? = null,
    val availableTags: List<MangaTag> = emptyList(),
    val catalogTags: List<MangaTag> = emptyList(),
    val selectedTags: Set<MangaTag> = emptySet(),
    val selectedTagsExclude: Set<MangaTag> = emptySet(),
    val isTagsExclusionSupported: Boolean = false,
    val availableStates: List<MangaState> = emptyList(),
    val selectedStates: Set<MangaState> = emptySet(),
    val availableContentRating: List<ContentRating> = emptyList(),
    val selectedContentRating: Set<ContentRating> = emptySet(),
    val availableTypes: List<ContentType> = emptyList(),
    val selectedTypes: Set<ContentType> = emptySet(),
    val availableDemographics: List<Demographic> = emptyList(),
    val selectedDemographics: Set<Demographic> = emptySet(),
    val availableLocales: List<Locale> = emptyList(),
    val selectedLocale: Locale? = null,
    val selectedOriginalLocale: Locale? = null,
    val isOriginalLocaleSupported: Boolean = false,
    val isYearSupported: Boolean = false,
    val isYearRangeSupported: Boolean = false,
    val selectedYear: Int = 0,
    val selectedYearFrom: Int = 0,
    val selectedYearTo: Int = 0,
    val isAuthorSearchSupported: Boolean = false,
    val author: String? = null,
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
