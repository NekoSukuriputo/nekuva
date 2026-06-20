package org.nekosukuriputo.nekuva.favourites.ui.container

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

class FavouritesViewModel(
    private val repository: FavouritesRepository,
    private val settings: AppSettings
) : ViewModel() {

    // Tabs show only library-visible categories (Doki observeCategoriesForLibrary); the default
    // category (id 0) is not a tab — its favourites appear under "All favourites".
    val categories: StateFlow<List<FavouriteCategory>> = repository.observeCategoriesForLibrary()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isAllFavouritesVisible = settings.isAllFavouritesVisibleFlow

    /** Hide a tab (Doki popup_fav_tab action_hide): the "All" pseudo-tab, or a real category. */
    fun hide(categoryId: Long) {
        viewModelScope.launch {
            if (categoryId == ALL_CATEGORY_ID) {
                settings.isAllFavouritesVisible = false
            } else {
                runCatching { repository.setCategoryVisibility(categoryId, false) }
            }
        }
    }

    /** Delete a category (Doki popup_fav_tab action_delete). */
    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { runCatching { repository.removeCategories(setOf(categoryId)) } }
    }

    companion object {
        const val ALL_CATEGORY_ID = -1L
    }
}
