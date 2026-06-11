package org.nekosukuriputo.nekuva.local.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.domain.model.LocalManga

class LocalListViewModel(
    private val localMangaRepository: LocalMangaRepository,
    private val localStorageChanges: MutableSharedFlow<LocalManga?>,
    private val mangaDataRepository: org.nekosukuriputo.nekuva.core.parser.MangaDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocalListUiState>(LocalListUiState.Loading)
    val uiState: StateFlow<LocalListUiState> = _uiState.asStateFlow()

    init {
        loadManga()
        // Refresh when storage changes (e.g. a download finished, or a manga was deleted).
        viewModelScope.launch {
            localStorageChanges.collect { loadManga() }
        }
    }

    fun deleteManga(manga: org.nekosukuriputo.nekuva.parsers.model.Manga) {
        viewModelScope.launch {
            runCatching { localMangaRepository.delete(manga) }
            loadManga()
        }
    }

    fun loadManga() {
        viewModelScope.launch {
            _uiState.value = LocalListUiState.Loading
            try {
                // For simplicity in the vertical slice, we just load the whole list at once
                val list = localMangaRepository.getList(0, null, null)
                // Store local manga in the DB so Details/Reader can resolve them by id.
                list.forEach { runCatching { mangaDataRepository.storeManga(it, replaceExisting = false) } }
                if (list.isEmpty()) {
                    _uiState.value = LocalListUiState.Empty
                } else {
                    _uiState.value = LocalListUiState.Success(list)
                }
            } catch (e: Exception) {
                _uiState.value = LocalListUiState.Error(e)
            }
        }
    }
}

sealed interface LocalListUiState {
    data object Loading : LocalListUiState
    data object Empty : LocalListUiState
    data class Success(val mangaList: List<org.nekosukuriputo.nekuva.parsers.model.Manga>) : LocalListUiState
    data class Error(val exception: Throwable) : LocalListUiState
}

