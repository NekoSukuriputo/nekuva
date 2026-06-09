package org.nekosukuriputo.nekuva.search.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
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
import org.nekosukuriputo.nekuva.core.nav.GlobalSearchRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.MangaSourcesRepository
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaListFilter
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable

private const val MAX_PARALLELISM = 4
private const val DB_SECTION_LIMIT = 20

enum class SearchSectionKind { HISTORY, FAVOURITES, LOCAL, SOURCE }

/** One result group in the global search (a DB section or a parser-source section). */
data class SearchSection(
    val id: String,
    val kind: SearchSectionKind,
    /** Display title for SOURCE/LOCAL sections (source name). HISTORY/FAVOURITES use a string resource. */
    val sourceName: String,
    /** Parser source name for "see all" navigation (SOURCE only). */
    val sourceId: String?,
    val manga: List<Manga>,
    val error: Throwable?,
)

data class GlobalSearchUiState(
    val sections: List<SearchSection> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Global multi-source search (mirrors Doki's SearchViewModel): searches History, Favourites and Local
 * (DB/local, read-only) first, then every enabled parser source in parallel (Semaphore-limited),
 * appending each section as it completes (streaming). Per-source failures become error sections; they
 * are never swallowed silently. Sources that need stubbed evaluateJs are expected to error.
 */
class GlobalSearchViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositoryFactory: MangaRepository.Factory,
    private val mangaDataRepository: MangaDataRepository,
    private val sourcesRepository: MangaSourcesRepository,
    private val historyRepository: HistoryRepository,
    private val favouritesRepository: FavouritesRepository,
    private val localMangaRepository: LocalMangaRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GlobalSearchRoute>()
    val query: String = route.query

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private val sections = LinkedHashMap<String, SearchSection>()
    private val mutex = Mutex()
    private var searchJob: Job? = null

    init {
        doSearch()
    }

    fun retry() = doSearch()

    private fun doSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutex.withLock {
                sections.clear()
                _uiState.value = GlobalSearchUiState(emptyList(), isLoading = true)
            }
            val skipNsfw = settings.isNsfwContentDisabled

            // DB / local sections first (no network, no JS).
            appendSection(searchHistory(skipNsfw))
            appendSection(searchFavourites(skipNsfw))
            appendSection(searchLocal(skipNsfw))

            // Enabled parser sources, in parallel with a concurrency limit; stream as each finishes.
            val sources = runCatchingCancellable { sourcesRepository.getEnabledSources() }.getOrDefault(emptyList())
            val semaphore = Semaphore(MAX_PARALLELISM)
            sources.map { source ->
                launch {
                    semaphore.withPermit {
                        appendSection(searchSource(source, skipNsfw))
                    }
                }
            }.joinAll()

            mutex.withLock {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun appendSection(section: SearchSection?) {
        if (section == null) return
        mutex.withLock {
            sections[section.id] = section
            _uiState.value = _uiState.value.copy(sections = sections.values.toList())
        }
    }

    private suspend fun searchSource(source: MangaSource, skipNsfw: Boolean): SearchSection? {
        return runCatchingCancellable {
            val repo = repositoryFactory.create(source)
            if (!repo.filterCapabilities.isSearchSupported) return null
            val sortOrder = if (SortOrder.RELEVANCE in repo.sortOrders) SortOrder.RELEVANCE else repo.defaultSortOrder
            repo.getList(0, sortOrder, MangaListFilter(query = query))
        }.fold(
            onSuccess = { list ->
                // distinctBy id: a manga can appear more than once (e.g. in several favourite
                // categories), which would crash the LazyRow on duplicate keys.
                val filtered = (if (skipNsfw) list.filterNot { it.isNsfw() } else list).distinctBy { it.id }
                if (filtered.isEmpty()) {
                    null
                } else {
                    filtered.forEach { mangaDataRepository.storeManga(it, replaceExisting = false) }
                    SearchSection(
                        id = "src:${source.name}",
                        kind = SearchSectionKind.SOURCE,
                        sourceName = source.name,
                        sourceId = source.name,
                        manga = filtered,
                        error = null,
                    )
                }
            },
            onFailure = { error ->
                SearchSection(
                    id = "src:${source.name}",
                    kind = SearchSectionKind.SOURCE,
                    sourceName = source.name,
                    sourceId = source.name,
                    manga = emptyList(),
                    error = error,
                )
            },
        )
    }

    private suspend fun searchHistory(skipNsfw: Boolean): SearchSection? = runCatchingCancellable {
        historyRepository.search(query, DB_SECTION_LIMIT)
    }.fold(
        onSuccess = { list -> dbSection("history", SearchSectionKind.HISTORY, list, skipNsfw) },
        onFailure = { errorSection("history", SearchSectionKind.HISTORY, it) },
    )

    private suspend fun searchFavourites(skipNsfw: Boolean): SearchSection? = runCatchingCancellable {
        favouritesRepository.search(query, DB_SECTION_LIMIT)
    }.fold(
        onSuccess = { list -> dbSection("favourites", SearchSectionKind.FAVOURITES, list, skipNsfw) },
        onFailure = { errorSection("favourites", SearchSectionKind.FAVOURITES, it) },
    )

    private suspend fun searchLocal(skipNsfw: Boolean): SearchSection? = runCatchingCancellable {
        localMangaRepository.getList(0, SortOrder.RELEVANCE, MangaListFilter(query = query))
    }.fold(
        onSuccess = { list -> dbSection("local", SearchSectionKind.LOCAL, list, skipNsfw) },
        onFailure = { errorSection("local", SearchSectionKind.LOCAL, it) },
    )

    private fun dbSection(id: String, kind: SearchSectionKind, list: List<Manga>, skipNsfw: Boolean): SearchSection? {
        // distinctBy id: favourites JOIN can yield the same manga once per category.
        val filtered = (if (skipNsfw) list.filterNot { it.isNsfw() } else list).distinctBy { it.id }
        return if (filtered.isEmpty()) null else SearchSection(id, kind, "", null, filtered, null)
    }

    private fun errorSection(id: String, kind: SearchSectionKind, error: Throwable) =
        SearchSection(id, kind, "", null, emptyList(), error)
}
