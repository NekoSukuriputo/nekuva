package org.nekosukuriputo.nekuva.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.history.domain.model.MangaWithHistory
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.parsers.model.Manga
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val markAsReadUseCase: org.nekosukuriputo.nekuva.history.domain.MarkAsReadUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            historyRepository.observeAllWithHistory(
                order = ListSortOrder.LAST_READ,
                filterOptions = emptySet(),
                limit = Int.MAX_VALUE
            )
                .catch { e ->
                    _uiState.value = HistoryUiState.Error(e)
                }
                .collectLatest { list ->
                    _uiState.value = HistoryUiState.Success(list)
                }
        }
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
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val list: List<MangaWithHistory>) : HistoryUiState
    data class Error(val error: Throwable) : HistoryUiState
}
