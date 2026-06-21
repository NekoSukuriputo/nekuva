package org.nekosukuriputo.nekuva.explore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nekosukuriputo.nekuva.core.model.MangaSourceInfo
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.explore.domain.ExploreRepository
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

class ExploreViewModel(
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
	private val exploreRepository: ExploreRepository,
	private val mangaDataRepository: MangaDataRepository,
) : ViewModel() {

	/** "Open random" in progress (Doki isRandomLoading) — disables the button + shows a spinner. */
	private val _isRandomLoading = MutableStateFlow(false)
	val isRandomLoading: StateFlow<Boolean> = _isRandomLoading.asStateFlow()

	/** Doki openRandom: resolve a random manga, store it, then hand its id back for navigation. */
	fun openRandom(onResolved: (Long) -> Unit) {
		if (_isRandomLoading.value) return
		viewModelScope.launch {
			_isRandomLoading.value = true
			try {
				val manga = runCatchingCancellable { exploreRepository.findRandomManga(tagsLimit = 8) }.getOrNull()
				if (manga != null) {
					runCatchingCancellable { mangaDataRepository.storeManga(manga, replaceExisting = false) }
					onResolved(manga.id)
				}
			} finally {
				_isRandomLoading.value = false
			}
		}
	}

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

	// --- Source multi-select (Doki SourceSelectionDecoration + mode_source): long-press → select → act. ---

	/** Selected source names (Doki tracks selection ids). */
	private val _selected = MutableStateFlow<Set<String>>(emptySet())
	val selected: StateFlow<Set<String>> = _selected.asStateFlow()

	fun toggleSelection(source: MangaSourceInfo) {
		_selected.value = _selected.value.toMutableSet().also {
			if (!it.remove(source.mangaSource.name)) it.add(source.mangaSource.name)
		}
	}

	fun clearSelection() {
		_selected.value = emptySet()
	}

	private fun selectedSources(): List<MangaSourceInfo> {
		val names = _selected.value
		return (uiState.value as? ExploreUiState.Success)?.sources?.filter { it.mangaSource.name in names }.orEmpty()
	}

	/** Pin/unpin the selected sources (Doki action_pin/action_unpin) — pinned sort first. */
	fun pinSelected(isPinned: Boolean) {
		val sources = selectedSources().map { it.mangaSource }
		clearSelection()
		viewModelScope.launch { sourcesRepository.setSourcesPinned(sources, isPinned) }
	}

	/** Disable (hide) the selected sources (Doki action_disable). */
	fun disableSelected() {
		val sources = selectedSources().map { it.mangaSource }
		clearSelection()
		viewModelScope.launch { sourcesRepository.setSourcesEnabled(sources, isEnabled = false) }
	}
}

sealed interface ExploreUiState {
	data object Loading : ExploreUiState
	data object Empty : ExploreUiState
	data class Success(val sources: List<MangaSourceInfo>) : ExploreUiState
}
