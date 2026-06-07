package org.nekosukuriputo.nekuva.reader.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.nav.ReaderRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

class ReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val localMangaRepository: LocalMangaRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReaderRoute>()
    private val mangaId = route.mangaId
    private val chapterId = route.chapterId

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadPages()
    }

    private fun loadPages() {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                // 1. Find Manga in DB
                val manga = mangaDataRepository.findMangaById(mangaId, withChapters = true)
                if (manga == null) {
                    _uiState.value = ReaderUiState.Error(IllegalArgumentException("Manga with ID $mangaId not found."))
                    return@launch
                }

                // 2. Find Chapter
                val chapter = manga.chapters?.find { it.id == chapterId }
                if (chapter == null) {
                    _uiState.value = ReaderUiState.Error(IllegalArgumentException("Chapter with ID $chapterId not found."))
                    return@launch
                }

                // 3. Get repository
                val mangaRepository = if (manga.source.name == "LOCAL") {
                    localMangaRepository
                } else {
                    val source = MangaParserSource.entries.find { it.name == manga.source.name }
                    if (source != null) {
                        repositoryFactory.create(source)
                    } else {
                        throw IllegalArgumentException("Unknown source: ${manga.source.name}")
                    }
                }

                // 4. Fetch Pages
                val pages = mangaRepository.getPages(chapter)

                _uiState.value = ReaderUiState.Success(manga, chapter, pages)
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e)
            }
        }
    }

    fun retry() {
        loadPages()
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Success(
        val manga: Manga,
        val chapter: MangaChapter,
        val pages: List<MangaPage>
    ) : ReaderUiState
    data class Error(val exception: Throwable) : ReaderUiState
}
