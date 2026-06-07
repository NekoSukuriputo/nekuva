package org.nekosukuriputo.nekuva.favourites.ui.container

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository

class FavouritesViewModel(
    repository: FavouritesRepository
) : ViewModel() {

    val categories: StateFlow<List<FavouriteCategory>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
