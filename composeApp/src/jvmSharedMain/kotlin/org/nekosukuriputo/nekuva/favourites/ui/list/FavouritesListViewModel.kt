package org.nekosukuriputo.nekuva.favourites.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga

class FavouritesListViewModel(
    private val categoryId: Long,
    private val repository: FavouritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavouritesUiState>(FavouritesUiState.Loading)
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<FavouriteCategory>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFavourites()
    }

    private fun loadFavourites() {
        viewModelScope.launch {
            val order = ListSortOrder.NEWEST // Using a default since we simplified
            val flow = if (categoryId == 0L) {
                repository.observeAll(order, emptySet(), Int.MAX_VALUE)
            } else {
                repository.observeAll(categoryId, order, emptySet(), Int.MAX_VALUE)
            }

            flow.catch { e ->
                _uiState.value = FavouritesUiState.Error(e)
            }.collect { mangas ->
                if (mangas.isEmpty()) {
                    _uiState.value = FavouritesUiState.Empty
                } else {
                    _uiState.value = FavouritesUiState.Success(mangas)
                }
            }
        }
    }

    fun removeFromFavourites(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (categoryId == 0L) {
                repository.removeFromFavourites(ids)
            } else {
                repository.removeFromCategory(categoryId, ids)
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
            repository.createCategory(title, ListSortOrder.NEWEST, false, true)
        }
    }
}

sealed interface FavouritesUiState {
    data object Loading : FavouritesUiState
    data object Empty : FavouritesUiState
    data class Success(val mangas: List<Manga>) : FavouritesUiState
    data class Error(val exception: Throwable) : FavouritesUiState
}
