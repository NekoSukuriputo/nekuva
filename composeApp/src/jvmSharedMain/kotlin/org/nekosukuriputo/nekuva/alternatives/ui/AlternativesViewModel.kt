package org.nekosukuriputo.nekuva.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.nav.AlternativesRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

private const val MAX_PARALLELISM = 4

data class AlternativesUiState(
    val refManga: Manga? = null,
    val results: List<Manga> = emptyList(),
    val isLoading: Boolean = true,
    /** Footer "Search disabled sources" is offered once the enabled-source search has settled. */
    val canSearchDisabled: Boolean = false,
)

/**
 * "Find similar in other sources" (Doki AlternativesUseCase/AlternativesActivity): searches the reference
 * manga's title across every enabled parser source in parallel (Semaphore-limited), streaming matches into
 * one flat list as each source completes. The reference manga and its own source are excluded. The disabled
 * sources are searched on demand. Migrate/AutoFix (swap a favourite/history to another source) is deferred.
 */
class AlternativesViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val sourcesRepository: MangaSourcesRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val mangaId: Long = savedStateHandle.toRoute<AlternativesRoute>().mangaId

    private val _uiState = MutableStateFlow(AlternativesUiState())
    val uiState: StateFlow<AlternativesUiState> = _uiState.asStateFlow()

    private val found = LinkedHashMap<Long, Manga>()
    private val mutex = Mutex()
    private var searchJob: Job? = null
    private var includeDisabled = false
    private var refManga: Manga? = null

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
            val skipNsfw = settings.isNsfwContentDisabled
            val sources = runCatchingCancellable { sourcesRepository.getEnabledSources() }.getOrDefault(emptyList())
                .filter { it.name != manga.source.name }
            searchSources(manga, sources, skipNsfw)
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
            val skipNsfw = settings.isNsfwContentDisabled
            val disabled = runCatchingCancellable { sourcesRepository.getDisabledSources().toList() }
                .getOrDefault(emptyList())
                .filter { it.name != manga.source.name }
            searchSources(manga, disabled, skipNsfw)
            mutex.withLock { _uiState.value = _uiState.value.copy(isLoading = false, canSearchDisabled = false) }
        }
    }

    private suspend fun searchSources(manga: Manga, sources: List<MangaSource>, skipNsfw: Boolean) = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLELISM)
        sources.map { source ->
            launch {
                semaphore.withPermit { searchSource(manga, source, skipNsfw) }
            }
        }.joinAll()
    }

    private suspend fun searchSource(manga: Manga, source: MangaSource, skipNsfw: Boolean) {
        val matches = runCatchingCancellable {
            val repo = repositoryFactory.create(source)
            if (!repo.filterCapabilities.isSearchSupported) return
            val order = if (SortOrder.RELEVANCE in repo.sortOrders) SortOrder.RELEVANCE else repo.defaultSortOrder
            repo.getList(0, order, MangaListFilter(query = manga.title))
        }.getOrNull() ?: return
        val filtered = (if (skipNsfw) matches.filterNot { it.isNsfw() } else matches)
            .filter { it.id != manga.id }
        if (filtered.isEmpty()) return
        mutex.withLock {
            filtered.forEach { m ->
                if (m.id !in found) {
                    runCatchingCancellable { mangaDataRepository.storeManga(m, replaceExisting = false) }
                    found[m.id] = m
                }
            }
            _uiState.value = _uiState.value.copy(results = found.values.toList())
        }
    }
}
