package org.nekosukuriputo.nekuva.favourites.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

class CategoryListViewModel(
    private val repository: FavouritesRepository,
    private val settings: AppSettings
) : ViewModel() {

    val categories: StateFlow<List<FavouriteCategory>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isAllFavouritesVisible = settings.isAllFavouritesVisibleFlow

    fun toggleAllFavouritesVisibility() {
        settings.isAllFavouritesVisible = !settings.isAllFavouritesVisible
    }

    fun createCategory(title: String) {
        viewModelScope.launch {
            repository.createCategory(
                title = title,
                sortOrder = ListSortOrder.NEWEST,
                isTrackerEnabled = false,
                isVisibleOnShelf = true
            )
        }
    }

    fun updateCategory(category: FavouriteCategory, newTitle: String) {
        viewModelScope.launch {
            repository.updateCategory(
                id = category.id,
                title = newTitle,
                sortOrder = ListSortOrder(category.sortKey.toString(), ListSortOrder.NEWEST), // we might not have the correct current order, but updating just title for now
                isTrackerEnabled = category.isTrackingEnabled,
                isVisibleOnShelf = category.isVisibleInLibrary
            )
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            repository.removeCategories(listOf(categoryId))
        }
    }

    fun reorderCategories(orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderCategories(orderedIds)
        }
    }
}
