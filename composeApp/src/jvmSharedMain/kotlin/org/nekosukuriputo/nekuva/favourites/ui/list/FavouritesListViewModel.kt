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
