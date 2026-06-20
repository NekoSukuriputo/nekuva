package org.nekosukuriputo.nekuva.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nekosukuriputo.nekuva.alternatives.domain.AlternativesUseCase
import org.nekosukuriputo.nekuva.alternatives.domain.AutoFixUseCase
import org.nekosukuriputo.nekuva.alternatives.domain.MigrateUseCase
import org.nekosukuriputo.nekuva.core.nav.AlternativesRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

data class AlternativesUiState(
    val refManga: Manga? = null,
    val results: List<Manga> = emptyList(),
    val isLoading: Boolean = true,
    /** Footer "Search disabled sources" is offered once the enabled-source search has settled. */
    val canSearchDisabled: Boolean = false,
)

/**
 * "Find similar in other sources" (Doki AlternativesActivity): streams matches from [AlternativesUseCase]
 * into one flat list, and offers Migrate / AutoFix to swap a favourite/history to another source.
 */
class AlternativesViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val alternativesUseCase: AlternativesUseCase,
    private val migrateUseCase: MigrateUseCase,
    private val autoFixUseCase: AutoFixUseCase,
) : ViewModel() {

    private val mangaId: Long = savedStateHandle.toRoute<AlternativesRoute>().mangaId

    private val _uiState = MutableStateFlow(AlternativesUiState())
    val uiState: StateFlow<AlternativesUiState> = _uiState.asStateFlow()

    private val found = LinkedHashMap<Long, Manga>()
    private val mutex = Mutex()
    private var searchJob: Job? = null
    private var includeDisabled = false
    private var refManga: Manga? = null

    /** One-shot "migrating…" flag so the UI can block re-taps and show progress. */
    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating.asStateFlow()

    init {
        start()
    }

    fun retry() {
        includeDisabled = false
        start()
    }

    private fun start() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val manga = runCatchingCancellable { mangaDataRepository.findMangaById(mangaId, withChapters = false) }
                .getOrNull()
            refManga = manga
            mutex.withLock {
                found.clear()
                _uiState.value = AlternativesUiState(refManga = manga, isLoading = manga != null)
            }
            if (manga == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            runCatchingCancellable {
                alternativesUseCase(manga, throughDisabledSources = false).collect { append(it) }
            }
            mutex.withLock {
                _uiState.value = _uiState.value.copy(isLoading = false, canSearchDisabled = !includeDisabled)
            }
        }
    }

    /** Doki throughDisabledSources: search the sources the user hasn't enabled, on demand. */
    fun searchDisabled() {
        if (includeDisabled) return
        includeDisabled = true
        val manga = refManga ?: return
        viewModelScope.launch {
            searchJob?.join()
            mutex.withLock { _uiState.value = _uiState.value.copy(isLoading = true, canSearchDisabled = false) }
            runCatchingCancellable {
                alternativesUseCase(manga, throughDisabledSources = true).collect { append(it) }
            }
            mutex.withLock { _uiState.value = _uiState.value.copy(isLoading = false, canSearchDisabled = false) }
        }
    }

    /** Migrate the reference manga to [target] (Doki MigrateUseCase); [onDone] gets the new manga id. */
    fun migrateTo(target: Manga, onDone: (Long) -> Unit) {
        val old = refManga ?: return
        if (_isMigrating.value) return
        viewModelScope.launch {
            _isMigrating.value = true
            runCatching { migrateUseCase(old, target) }
            _isMigrating.value = false
            onDone(target.id)
        }
    }

    /** Auto-fix: pick the best alternative and migrate to it (Doki AutoFixUseCase); [onDone] gets the new id. */
    fun autoFix(onDone: (Long) -> Unit) {
        val old = refManga ?: return
        if (_isMigrating.value) return
        viewModelScope.launch {
            _isMigrating.value = true
            val chosen = runCatching { autoFixUseCase(old, found.values.toList()) }.getOrNull()
            _isMigrating.value = false
            if (chosen != null) onDone(chosen.id)
        }
    }

    private suspend fun append(manga: Manga) {
        mutex.withLock {
            if (manga.id !in found) {
                found[manga.id] = manga
                _uiState.value = _uiState.value.copy(results = found.values.toList())
            }
        }
    }
}
