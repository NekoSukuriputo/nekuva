package org.nekosukuriputo.nekuva.stats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.stats.data.StatsRepository
import org.nekosukuriputo.nekuva.stats.domain.StatsPeriod
import org.nekosukuriputo.nekuva.stats.domain.StatsRecord

/** Drives the reading-stats screen (port of Doki StatsViewModel): period + category filter → per-manga time. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repository: StatsRepository,
    favouritesRepository: FavouritesRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.WEEK)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCategories: StateFlow<Set<Long>> = _selectedCategories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val categories: StateFlow<List<FavouriteCategory>> = favouritesRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readingStats: StateFlow<List<StatsRecord>> =
        combine(_period, _selectedCategories) { p, c -> p to c }
            .mapLatest { (p, c) ->
                _isLoading.value = true
                try {
                    repository.getReadingStats(p, c)
                } finally {
                    _isLoading.value = false
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPeriod(value: StatsPeriod) {
        _period.value = value
    }

    fun setCategoryChecked(categoryId: Long, checked: Boolean) {
        _selectedCategories.value = _selectedCategories.value.toMutableSet().apply {
            if (checked) add(categoryId) else remove(categoryId)
        }
    }

    fun clearStats() {
        viewModelScope.launch { repository.clearStats() }
    }
}
