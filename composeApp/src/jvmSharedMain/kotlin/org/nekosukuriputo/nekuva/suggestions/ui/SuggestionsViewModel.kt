package org.nekosukuriputo.nekuva.suggestions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.suggestions.domain.GenerateSuggestionsUseCase
import org.nekosukuriputo.nekuva.suggestions.domain.SuggestionRepository

/** Drives the suggestions screen (port of Doki SuggestionsViewModel): observe stored list + on-demand refresh. */
class SuggestionsViewModel(
    private val repository: SuggestionRepository,
    private val generate: GenerateSuggestionsUseCase,
    private val settings: AppSettings,
) : ViewModel() {

    val suggestions: StateFlow<List<Manga>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isEnabled: Boolean get() = settings.isSuggestionsEnabled

    init {
        // Generate on first open when enabled but nothing stored yet (Doki seeds on the periodic worker).
        viewModelScope.launch {
            if (settings.isSuggestionsEnabled && runCatchingCancellable { repository.isEmpty() }.getOrDefault(false)) {
                refresh()
            }
        }
    }

    fun refresh() {
        if (!settings.isSuggestionsEnabled) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                runCatchingCancellable { generate() }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
