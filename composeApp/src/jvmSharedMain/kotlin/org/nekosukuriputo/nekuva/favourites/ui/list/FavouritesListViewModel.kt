package org.nekosukuriputo.nekuva.favourites.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavouritesListViewModel(
    private val categoryId: Long,
    private val repository: FavouritesRepository,
    private val markAsReadUseCase: org.nekosukuriputo.nekuva.history.domain.MarkAsReadUseCase,
    private val settings: org.nekosukuriputo.nekuva.core.prefs.AppSettings,
    private val downloadManager: org.nekosukuriputo.nekuva.download.domain.DownloadManager,
    private val alternativesUseCase: org.nekosukuriputo.nekuva.alternatives.domain.AlternativesUseCase,
    private val autoFixUseCase: org.nekosukuriputo.nekuva.alternatives.domain.AutoFixUseCase,
    private val mangaDataRepository: org.nekosukuriputo.nekuva.core.parser.MangaDataRepository,
) : ViewModel() {

    val categories: StateFlow<List<FavouriteCategory>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive on the global favourites sort (Doki KEY_FAVORITES_ORDER): re-query when it changes.
    val uiState: StateFlow<FavouritesUiState> = settings.keyChangeFlow(AppSettings.KEY_FAVORITES_ORDER)
        .onStart { emit(Unit) }
        .flatMapLatest {
            val order = settings.allFavoritesSortOrder
            val flow = if (categoryId == -1L) {
                repository.observeAll(order, emptySet(), Int.MAX_VALUE)
            } else {
                repository.observeAll(categoryId, order, emptySet(), Int.MAX_VALUE)
            }
            flow.map<List<Manga>, FavouritesUiState> { if (it.isEmpty()) FavouritesUiState.Empty else FavouritesUiState.Success(it) }
                .catch { e -> emit(FavouritesUiState.Error(e)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FavouritesUiState.Loading)

    fun removeFromFavourites(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (categoryId == -1L || categoryId == 0L) {
                repository.removeFromFavourites(ids)
            } else {
                repository.removeFromCategory(categoryId, ids)
            }
        }
    }

    /** Selection-mode: mark several favourites as fully read (Doki action_mark_current). */
    fun markAsRead(mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch { runCatching { markAsReadUseCase(mangas) } }
    }

    /** Selection-mode: download the whole of each selected manga (Doki action_save). */
    fun downloadManga(mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                downloadManager.schedule(
                    mangas.map {
                        org.nekosukuriputo.nekuva.download.domain.DownloadTask(
                            manga = it, chaptersIds = null, destination = null, format = null, startPaused = false,
                        )
                    },
                )
            }
        }
    }

    /** Selection-mode: add selected manga to the chosen favourite categories (Doki action_favourite). */
    fun addToCategories(categoryIds: Set<Long>, mangas: Collection<Manga>) {
        if (categoryIds.isEmpty() || mangas.isEmpty()) return
        viewModelScope.launch {
            categoryIds.forEach { id -> runCatching { repository.addToCategory(id, mangas.toList()) } }
        }
    }

    /** Selection-mode: auto-fix each selected manga — best alternative source + migrate (Doki action_fix). */
    fun autoFix(mangas: Collection<Manga>) {
        if (mangas.isEmpty()) return
        viewModelScope.launch {
            for (manga in mangas) {
                runCatching {
                    val matches = ArrayList<Manga>()
                    alternativesUseCase(manga, throughDisabledSources = false).collect { matches.add(it) }
                    autoFixUseCase(manga, matches)
                }
            }
        }
    }

    /** Selection-mode (single): save a custom title/cover override (Doki action_edit_override). */
    fun setOverride(manga: Manga, title: String?, coverUrl: String?) {
        viewModelScope.launch {
            val existing = runCatching { mangaDataRepository.getOverride(manga.id) }.getOrNull()
            runCatching {
                mangaDataRepository.setOverride(
                    manga,
                    org.nekosukuriputo.nekuva.core.model.MangaOverride(
                        coverUrl = coverUrl?.trim()?.ifEmpty { null },
                        title = title?.trim()?.ifEmpty { null },
                        contentRating = existing?.contentRating,
                    ),
                )
            }
        }
    }

    fun removeCategories(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.removeCategories(ids)
        }
    }

    fun createCategory(title: String) {
        viewModelScope.launch {
            repository.createCategory(title, ListSortOrder.NEWEST, true, true)
        }
    }
}

sealed interface FavouritesUiState {
    data object Loading : FavouritesUiState
    data object Empty : FavouritesUiState
    data class Success(val mangas: List<Manga>) : FavouritesUiState
    data class Error(val exception: Throwable) : FavouritesUiState
}
