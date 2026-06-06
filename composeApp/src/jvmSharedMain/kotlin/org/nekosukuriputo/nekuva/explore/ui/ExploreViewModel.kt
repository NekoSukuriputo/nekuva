package org.nekosukuriputo.nekuva.explore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nekosukuriputo.nekuva.core.model.MangaSourceInfo
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository

class ExploreViewModel(
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
) : ViewModel() {

	val uiState: StateFlow<ExploreUiState> = sourcesRepository.observeEnabledSources()
		.map { sources ->
			if (sources.isEmpty()) {
				ExploreUiState.Empty
			} else {
				ExploreUiState.Success(sources)
			}
		}
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = ExploreUiState.Loading,
		)
}

sealed interface ExploreUiState {
	data object Loading : ExploreUiState
	data object Empty : ExploreUiState
	data class Success(val sources: List<MangaSourceInfo>) : ExploreUiState
}
