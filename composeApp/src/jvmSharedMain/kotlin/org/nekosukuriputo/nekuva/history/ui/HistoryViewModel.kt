package org.nekosukuriputo.nekuva.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.history.domain.model.MangaWithHistory
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.parsers.model.Manga
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val markAsReadUseCase: org.nekosukuriputo.nekuva.history.domain.MarkAsReadUseCase,
    private val settings: org.nekosukuriputo.nekuva.core.prefs.AppSettings,
    private val downloadManager: org.nekosukuriputo.nekuva.download.domain.DownloadManager,
    private val favouritesRepository: org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository,
    private val alternativesUseCase: org.nekosukuriputo.nekuva.alternatives.domain.AlternativesUseCase,
    private val autoFixUseCase: org.nekosukuriputo.nekuva.alternatives.domain.AutoFixUseCase,
    private val mangaDataRepository: org.nekosukuriputo.nekuva.core.parser.MangaDataRepository,
) : ViewModel() {

    /** Favourite categories for the "add to favourites" picker (Doki mode_history action_favourite). */
    val favouriteCategories: StateFlow<List<org.nekosukuriputo.nekuva.core.model.FavouriteCategory>> =
        favouritesRepository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pagination (Doki PAGE_SIZE / requestMoreItems): grow the DB window as the user scrolls.
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // Sort order (Doki history sort; persisted to KEY_HISTORY_ORDER).
    private val _sortOrder = MutableStateFlow(settings.historySortOrder)
    val sortOrder: StateFlow<ListSortOrder> = _sortOrder.asStateFlow()

    // Quick-filter chips (Doki HistoryListQuickFilter): applied ListFilterOptions narrowing the DB window.
    private val _filters = MutableStateFlow<Set<ListFilterOption>>(emptySet())
    val filters: StateFlow<Set<ListFilterOption>> = _filters.asStateFlow()

    // Available quick-filter chips incl. popular sources + tags (Doki getAvailableFilterOptions).
    private val _availableFilters = MutableStateFlow<List<ListFilterOption>>(emptyList())
    val availableFilters: StateFlow<List<ListFilterOption>> = _availableFilters.asStateFlow()

    init {
        loadAvailableFilters()
    }

    private fun loadAvailableFilters() {
        viewModelScope.launch {
            val tags = runCatching { historyRepository.getPopularTags(3) }.getOrDefault(emptyList())
            val sources = runCatching { historyRepository.getPopularSources(3) }.getOrDefault(emptyList())
            _availableFilters.value = buildList {
                add(ListFilterOption.Downloaded)
                if (settings.isTrackerEnabled) add(ListFilterOption.Macro.NEW_CHAPTERS)
                add(ListFilterOption.Macro.COMPLETED)
                add(ListFilterOption.Macro.FAVORITE)
                add(ListFilterOption.NOT_FAVORITE)
                if (!settings.isNsfwContentDisabled) add(ListFilterOption.Macro.NSFW)
                tags.forEach { add(ListFilterOption.Tag(it)) }
                sources.forEach { add(ListFilterOption.Source(it)) }
            }
        }
    }

    val uiState: StateFlow<HistoryUiState> =
        combine(limit, _sortOrder, _filters) { lim, order, filters -> Triple(lim, order, filters) }
            .flatMapLatest { (lim, order, filters) ->
                historyRepository.observeAllWithHistory(order, filters, lim)
                    .onEach { _hasMore.value = it.size >= lim }
                    .map<List<MangaWithHistory>, HistoryUiState> { HistoryUiState.Success(it) }
                    .catch { e -> emit(HistoryUiState.Error(e)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState.Loading)

    /** Change the sort order (Doki history sort), persist it, and restart pagination. */
    fun setSortOrder(order: ListSortOrder) {
        settings.historySortOrder = order
        limit.value = PAGE_SIZE
        _sortOrder.value = order
    }

    /** Toggle a quick-filter chip (Doki QuickFilterListener.onFilterOptionClick); restart pagination. */
    fun toggleFilter(option: ListFilterOption) {
        _filters.value = if (option in _filters.value) _filters.value - option else _filters.value + option
        limit.value = PAGE_SIZE
    }

    /** Load the next page when scrolled near the end (no-op when the last page was already full). */
    fun loadMore() {
        if (_hasMore.value) limit.value += PAGE_SIZE
    }

    fun removeHistory(manga: Manga) {
        viewModelScope.launch {
            historyRepository.delete(manga)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clear()
        }
    }

    /** Clear history entries updated at/after [minDateMillis] (Doki clearHistory(minDate)). */
    fun clearHistoryAfter(minDateMillis: Long) {
        viewModelScope.launch {
            historyRepository.deleteAfter(minDateMillis)
        }
    }

    /** Remove every history entry that is not in any favourite category (Doki removeNotFavorite). */
    fun removeNotFavorite() {
        viewModelScope.launch {
            historyRepository.deleteNotFavorite()
        }
    }

    /** Selection-mode: remove several manga from history (Doki mode_history action_remove). */
    fun removeHistory(mangas: Collection<Manga>) {
        viewModelScope.launch {
            mangas.forEach { historyRepository.delete(it) }
        }
    }

    /** Selection-mode: mark several manga as fully read (Doki action_mark_current). */
    fun markAsRead(mangas: Collection<Manga>) {
        viewModelScope.launch {
            runCatching { markAsReadUseCase(mangas) }
        }
    }

    /** Selection-mode: download the whole of each selected manga (Doki action_save). */
    fun downloadManga(mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                downloadManager.schedule(
                    mangas.map {
                        org.nekosukuriputo.nekuva.download.domain.DownloadTask(
                            manga = it, chaptersIds = null, destination = null, format = null, startPaused = false,
                        )
                    },
                )
            }
        }
    }

    /** Selection-mode: add several manga to a favourite category (Doki action_favourite). */
    fun addToFavourites(categoryId: Long, mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch {
            runCatching { favouritesRepository.addToCategory(categoryId, mangas.toList()) }
        }
    }

    /** Selection-mode: auto-fix each selected manga — find the best alternative source and migrate (Doki action_fix). */
    fun autoFix(mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch {
            for (manga in mangas) {
                runCatching {
                    val matches = ArrayList<Manga>()
                    alternativesUseCase(manga, throughDisabledSources = false).collect { matches.add(it) }
                    autoFixUseCase(manga, matches)
                }
            }
        }
    }

    /** Selection-mode (single): save a custom title/cover override (Doki action_edit_override). */
    fun setOverride(manga: Manga, title: String?, coverUrl: String?) {
        viewModelScope.launch {
            val existing = runCatching { mangaDataRepository.getOverride(manga.id) }.getOrNull()
            runCatching {
                mangaDataRepository.setOverride(
                    manga,
                    org.nekosukuriputo.nekuva.core.model.MangaOverride(
                        coverUrl = coverUrl?.trim()?.ifEmpty { null },
                        title = title?.trim()?.ifEmpty { null },
                        contentRating = existing?.contentRating,
                    ),
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val list: List<MangaWithHistory>) : HistoryUiState
    data class Error(val error: Throwable) : HistoryUiState
}
