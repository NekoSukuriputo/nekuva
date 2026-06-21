package org.nekosukuriputo.nekuva.details.ui.related

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.nav.RelatedRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

/**
 * Doki "Find similar" (RelatedListViewModel): fetches `repository.getRelated(seed)` for the opened manga and
 * exposes it as a simple list with loading / empty / error states.
 */
class RelatedViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
) : ViewModel() {

    private val mangaId: Long = savedStateHandle.toRoute<RelatedRoute>().mangaId

    private val _uiState = MutableStateFlow<RelatedUiState>(RelatedUiState.Loading)
    val uiState: StateFlow<RelatedUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.value = RelatedUiState.Loading
        viewModelScope.launch {
            try {
                val seed = mangaDataRepository.findMangaById(mangaId, withChapters = false)
                    ?: throw IllegalArgumentException("Manga $mangaId not found")
                val source = MangaParserSource.entries.find { it.name == seed.source.name }
                    ?: throw IllegalStateException("Unknown source: ${seed.source.name}")
                val related = repositoryFactory.create(source).getRelated(seed)
                // Persist so tapping a result can open Details by id (same as the remote list).
                related.forEach { runCatching { mangaDataRepository.storeManga(it, replaceExisting = false) } }
                _uiState.value = if (related.isEmpty()) RelatedUiState.Empty else RelatedUiState.Success(related)
            } catch (e: Exception) {
                _uiState.value = RelatedUiState.Error(e)
            }
        }
    }
}

sealed interface RelatedUiState {
    data object Loading : RelatedUiState
    data object Empty : RelatedUiState
    data class Success(val mangaList: List<Manga>) : RelatedUiState
    data class Error(val exception: Throwable) : RelatedUiState
}
