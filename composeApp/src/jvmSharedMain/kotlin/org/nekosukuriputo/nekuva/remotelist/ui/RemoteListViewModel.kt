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

import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository

class RemoteListViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<RemoteListRoute>()
    val sourceId = route.sourceId

    private val _uiState = MutableStateFlow<RemoteListUiState>(RemoteListUiState.Loading)
    val uiState: StateFlow<RemoteListUiState> = _uiState.asStateFlow()

    private var isAppendingJob: kotlinx.coroutines.Job? = null

    init {
        loadMangaList(append = false)
    }

    private fun loadMangaList(append: Boolean) {
        if (append && isAppendingJob?.isActive == true) return
        
        val job = viewModelScope.launch {
            val currentState = _uiState.value
            val prevList = if (append && currentState is RemoteListUiState.Success) {
                currentState.mangaList
            } else {
                emptyList()
            }

            if (append && currentState is RemoteListUiState.Success) {
                _uiState.value = currentState.copy(isAppending = true)
            } else {
                _uiState.value = RemoteListUiState.Loading
            }

            try {
                // Find the actual parser source by ID
                val parserSource = MangaParserSource.entries.find { it.name == sourceId }
                if (parserSource == null) {
                    _uiState.value = RemoteListUiState.Error(IllegalArgumentException("Source not found: $sourceId"))
                    return@launch
                }

                val repository = repositoryFactory.create(parserSource)
                
                val offset = prevList.size
                val list = repository.getList(offset = offset, order = null, filter = null)
                
                if (!append && list.isEmpty()) {
                    _uiState.value = RemoteListUiState.Empty
                } else {
                    // Cache the fetched manga list
                    list.forEach { manga ->
                        mangaDataRepository.storeManga(manga, replaceExisting = false)
                    }
                    val newList = (prevList + list).distinctBy { it.id }
                    _uiState.value = RemoteListUiState.Success(
                        mangaList = newList,
                        isAppending = false,
                        hasNextPage = list.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                if (append && currentState is RemoteListUiState.Success) {
                    _uiState.value = currentState.copy(isAppending = false)
                } else {
                    _uiState.value = RemoteListUiState.Error(e)
                }
            }
        }
        if (append) {
            isAppendingJob = job
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state is RemoteListUiState.Success && state.hasNextPage && !state.isAppending) {
            loadMangaList(append = true)
        }
    }

    fun retry() {
        loadMangaList(append = false)
    }
}

sealed interface RemoteListUiState {
    data object Loading : RemoteListUiState
    data object Empty : RemoteListUiState
    data class Success(
        val mangaList: List<Manga>,
        val isAppending: Boolean = false,
        val hasNextPage: Boolean = true
    ) : RemoteListUiState
    data class Error(val exception: Throwable) : RemoteListUiState
}
