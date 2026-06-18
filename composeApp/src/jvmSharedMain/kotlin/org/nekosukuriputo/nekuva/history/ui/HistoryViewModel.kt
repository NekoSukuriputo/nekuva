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
) : ViewModel() {

    // Pagination (Doki PAGE_SIZE / requestMoreItems): grow the DB window as the user scrolls.
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // Sort order (Doki history sort; persisted to KEY_HISTORY_ORDER).
    private val _sortOrder = MutableStateFlow(settings.historySortOrder)
    val sortOrder: StateFlow<ListSortOrder> = _sortOrder.asStateFlow()

    val uiState: StateFlow<HistoryUiState> =
        combine(limit, _sortOrder) { lim, order -> lim to order }
            .flatMapLatest { (lim, order) ->
                historyRepository.observeAllWithHistory(order, emptySet(), lim)
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

    private companion object {
        const val PAGE_SIZE = 20
    }
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val list: List<MangaWithHistory>) : HistoryUiState
    data class Error(val error: Throwable) : HistoryUiState
}
