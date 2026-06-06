package org.nekosukuriputo.nekuva.remotelist.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.nav.RemoteListRoute
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

class RemoteListViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<RemoteListRoute>()
    private val sourceId = route.sourceId

    private val _uiState = MutableStateFlow<RemoteListUiState>(RemoteListUiState.Loading)
    val uiState: StateFlow<RemoteListUiState> = _uiState.asStateFlow()

    init {
        loadMangaList()
    }

    private fun loadMangaList() {
        viewModelScope.launch {
            _uiState.value = RemoteListUiState.Loading
            try {
                // Find the actual parser source by ID
                val parserSource = MangaParserSource.entries.find { it.name == sourceId }
                if (parserSource == null) {
                    _uiState.value = RemoteListUiState.Error(IllegalArgumentException("Source not found: $sourceId"))
                    return@launch
                }

                val repository = repositoryFactory.create(parserSource)
                
                // For the proof-of-concept vertical slice, we just load the first page with no filter
                val list = repository.getList(offset = 0, order = null, filter = null)
                if (list.isEmpty()) {
                    _uiState.value = RemoteListUiState.Empty
                } else {
                    _uiState.value = RemoteListUiState.Success(list)
                }
            } catch (e: Exception) {
                _uiState.value = RemoteListUiState.Error(e)
            }
        }
    }

    fun retry() {
        loadMangaList()
    }
}

sealed interface RemoteListUiState {
    data object Loading : RemoteListUiState
    data object Empty : RemoteListUiState
    data class Success(val mangaList: List<Manga>) : RemoteListUiState
    data class Error(val exception: Throwable) : RemoteListUiState
}
