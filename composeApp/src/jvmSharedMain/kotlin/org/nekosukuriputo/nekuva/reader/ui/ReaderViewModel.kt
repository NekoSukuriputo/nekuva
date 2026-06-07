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
import org.nekosukuriputo.nekuva.history.domain.HistoryUpdateUseCase
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
    private val historyUpdateUseCase: HistoryUpdateUseCase,
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

                // 5. Check History for initial page
                val history = org.koin.mp.KoinPlatform.getKoin().get<org.nekosukuriputo.nekuva.history.data.HistoryRepository>().getOne(manga)
                val initialPage = if (history != null && history.chapterId == chapterId) history.page else 0

                _uiState.value = ReaderUiState.Success(manga, chapter, pages, initialPage)
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e)
            }
        }
    }

    fun retry() {
        loadPages()
    }

    fun onPageChanged(pageIndex: Int) {
        val state = _uiState.value
        if (state is ReaderUiState.Success) {
            val pageCount = state.pages.size
            if (pageCount == 0) return
            
            val percent = if (pageIndex == pageCount - 1) 1.0f else (pageIndex.toFloat() / pageCount.toFloat())
            
            historyUpdateUseCase.invokeAsync(
                manga = state.manga,
                chapterId = state.chapter.id,
                page = pageIndex,
                scroll = 0,
                percent = percent
            )
        }
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Success(
        val manga: Manga,
        val chapter: MangaChapter,
        val pages: List<MangaPage>,
        val initialPage: Int = 0
    ) : ReaderUiState
    data class Error(val exception: Throwable) : ReaderUiState
}
