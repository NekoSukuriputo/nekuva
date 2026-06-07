package org.nekosukuriputo.nekuva.details.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.nav.MangaDetailsRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

class DetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MangaDetailsRoute>()
    private val mangaId = route.mangaId

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            try {
                // 1. Find Manga in DB
                var manga = mangaDataRepository.findMangaById(mangaId, withChapters = true)
                if (manga == null) {
                    _uiState.value = DetailsUiState.Error(IllegalArgumentException("Manga with ID $mangaId not found in local cache."))
                    return@launch
                }
                
                // 2. Fetch details if it's from a parser source
                val source = MangaParserSource.entries.find { it.name == manga!!.source.name }
                if (source != null) {
                    val parserRepository = repositoryFactory.create(source)
                    manga = parserRepository.getDetails(manga!!)
                    // Save the fetched details and chapters to DB
                    mangaDataRepository.storeManga(manga!!, replaceExisting = true)
                }

                _uiState.value = DetailsUiState.Success(manga!!)
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error(e)
            }
        }
    }

    fun retry() {
        loadDetails()
    }
}

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(val manga: Manga) : DetailsUiState
    data class Error(val exception: Throwable) : DetailsUiState
}
