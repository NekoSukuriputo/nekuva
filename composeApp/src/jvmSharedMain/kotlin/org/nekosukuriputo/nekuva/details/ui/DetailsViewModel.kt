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
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.bookmarks.domain.BookmarksRepository
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val favouritesRepository: FavouritesRepository,
    private val historyRepository: org.nekosukuriputo.nekuva.history.data.HistoryRepository,
    private val bookmarksRepository: BookmarksRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MangaDetailsRoute>()
    private val mangaId = route.mangaId

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val loadedManga = MutableStateFlow<Manga?>(null)

    /** Bookmarks of the currently opened manga (shown in the chapters bottom sheet). */
    val bookmarks: StateFlow<List<Bookmark>> = loadedManga.filterNotNull()
        .flatMapLatest { bookmarksRepository.observeBookmarks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isFavorite: StateFlow<Boolean> = favouritesRepository.observeCategoriesIds(mangaId)
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allCategories: StateFlow<List<FavouriteCategory>> = favouritesRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mangaCategories: StateFlow<Set<Long>> = favouritesRepository.observeCategoriesIds(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val history: StateFlow<org.nekosukuriputo.nekuva.core.model.MangaHistory?> = historyRepository.observeOne(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                loadedManga.value = manga
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error(e)
            }
        }
    }

    fun retry() {
        loadDetails()
    }

    fun removeFromHistory() {
        viewModelScope.launch {
            val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return@launch
            historyRepository.delete(m)
        }
    }

    fun toggleCategory(categoryId: Long, isSelected: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state is DetailsUiState.Success) {
                if (isSelected) {
                    favouritesRepository.addToCategory(categoryId, listOf(state.manga))
                } else {
                    // Default (id 0) is a real category now — unchecking it removes from that category
                    // only, like any other category (not from all favourites).
                    favouritesRepository.removeFromCategory(categoryId, listOf(mangaId))
                }
            }
        }
    }
}

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(val manga: Manga) : DetailsUiState
    data class Error(val exception: Throwable) : DetailsUiState
}
