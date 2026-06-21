package org.nekosukuriputo.nekuva.search.ui

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
import org.nekosukuriputo.nekuva.core.nav.GlobalSearchRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
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
    /** For an errored SOURCE section: the source's site URL, for the "Open in browser" action. */
    val browserUrl: String? = null,
)

data class GlobalSearchUiState(
    val sections: List<SearchSection> = emptyList(),
    val isLoading: Boolean = true,
    /** Footer "Search disabled sources" is offered once the enabled-source search has settled. */
    val canSearchDisabled: Boolean = false,
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
    /** What the query means (Doki SearchKind): drives the per-source/DB filter (text / author / tag). */
    val kind: org.nekosukuriputo.nekuva.search.domain.SearchKind =
        runCatching { org.nekosukuriputo.nekuva.search.domain.SearchKind.valueOf(route.kind) }
            .getOrDefault(org.nekosukuriputo.nekuva.search.domain.SearchKind.SIMPLE)

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private val sections = LinkedHashMap<String, SearchSection>()
    private val mutex = Mutex()
    private var searchJob: Job? = null
    private var includeDisabled = false

    init {
        doSearch()
    }

    fun retry() {
        includeDisabled = false
        doSearch()
    }

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
            searchSources(sources, skipNsfw)

            mutex.withLock {
                _uiState.value = _uiState.value.copy(isLoading = false, canSearchDisabled = !includeDisabled)
            }
        }
    }

    /** Doki's continueSearch: search the sources the user hasn't enabled, on demand, appending sections. */
    fun continueSearch() {
        if (includeDisabled) return
        includeDisabled = true
        viewModelScope.launch {
            searchJob?.join()
            mutex.withLock {
                _uiState.value = _uiState.value.copy(isLoading = true, canSearchDisabled = false)
            }
            val skipNsfw = settings.isNsfwContentDisabled
            val disabled = runCatchingCancellable { sourcesRepository.getDisabledSources().toList() }
                .getOrDefault(emptyList())
            searchSources(disabled, skipNsfw)
            mutex.withLock {
                _uiState.value = _uiState.value.copy(isLoading = false, canSearchDisabled = false)
            }
        }
    }

    private suspend fun searchSources(sources: List<MangaSource>, skipNsfw: Boolean) = coroutineScope {
        val semaphore = Semaphore(MAX_PARALLELISM)
        sources.map { source ->
            launch {
                semaphore.withPermit {
                    appendSection(searchSource(source, skipNsfw))
                }
            }
        }.joinAll()
    }

    private suspend fun appendSection(section: SearchSection?) {
        if (section == null) return
        mutex.withLock {
            sections[section.id] = section
            _uiState.value = _uiState.value.copy(sections = sections.values.toList())
        }
    }

    private suspend fun searchSource(source: MangaSource, skipNsfw: Boolean): SearchSection? {
        val repo = runCatchingCancellable { repositoryFactory.create(source) }.getOrNull()
        // The source's site URL, used by "Open in browser" on an error section.
        val browserUrl = (repo as? ParserMangaRepository)?.let {
            runCatching { "https://${it.domain}" }.getOrNull()
        }
        return runCatchingCancellable {
            requireNotNull(repo) { "No repository for ${source.name}" }
            // Build the per-source filter from the search kind (Doki SearchV2Helper.getFilter); a source that
            // can't express this kind (e.g. no author/tag support) yields null → skip it.
            val filter = buildSourceFilter(repo, source) ?: return null
            val sortOrder = if (SortOrder.RELEVANCE in repo.sortOrders) SortOrder.RELEVANCE else repo.defaultSortOrder
            repo.getList(0, sortOrder, filter)
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
                    browserUrl = browserUrl,
                )
            },
        )
    }

    /**
     * Per-source filter for the current [kind] (Doki SearchV2Helper.getFilter): text → query, AUTHOR → author
     * (gated on capability, else query), TAG → resolve the tag by title in the source's tags. Returns null when
     * the source can't express this kind, so the caller skips it.
     */
    private suspend fun buildSourceFilter(repo: MangaRepository, source: MangaSource): MangaListFilter? =
        when (kind) {
            org.nekosukuriputo.nekuva.search.domain.SearchKind.AUTHOR -> when {
                repo.filterCapabilities.isAuthorSearchSupported -> MangaListFilter(author = query)
                repo.filterCapabilities.isSearchSupported -> MangaListFilter(query = query)
                else -> null
            }
            org.nekosukuriputo.nekuva.search.domain.SearchKind.TAG -> {
                val tags = runCatchingCancellable { mangaDataRepository.findTags(source) }.getOrDefault(emptyList()) +
                    runCatchingCancellable { repo.getFilterOptions().availableTags }.getOrDefault(emptySet())
                val tag = tags.find { it.title.equals(query, ignoreCase = true) }
                if (tag != null) MangaListFilter(tags = setOf(tag)) else null
            }
            else -> if (repo.filterCapabilities.isSearchSupported) MangaListFilter(query = query) else null
        }

    private suspend fun searchHistory(skipNsfw: Boolean): SearchSection? = runCatchingCancellable {
        historyRepository.search(query, DB_SECTION_LIMIT, kind)
    }.fold(
        onSuccess = { list -> dbSection("history", SearchSectionKind.HISTORY, list, skipNsfw) },
        onFailure = { errorSection("history", SearchSectionKind.HISTORY, it) },
    )

    private suspend fun searchFavourites(skipNsfw: Boolean): SearchSection? = runCatchingCancellable {
        favouritesRepository.search(query, DB_SECTION_LIMIT, kind)
    }.fold(
        onSuccess = { list -> dbSection("favourites", SearchSectionKind.FAVOURITES, list, skipNsfw) },
        onFailure = { errorSection("favourites", SearchSectionKind.FAVOURITES, it) },
    )

    private suspend fun searchLocal(skipNsfw: Boolean): SearchSection? = runCatchingCancellable {
        // Local has no author index → AUTHOR degrades to text; TAG matches by tag title (LocalMangaRepository).
        val filter = when (kind) {
            org.nekosukuriputo.nekuva.search.domain.SearchKind.TAG -> MangaListFilter(
                tags = setOf(
                    org.nekosukuriputo.nekuva.parsers.model.MangaTag(
                        title = query,
                        key = query,
                        source = org.nekosukuriputo.nekuva.core.model.LocalMangaSource,
                    ),
                ),
            )
            else -> MangaListFilter(query = query)
        }
        localMangaRepository.getList(0, SortOrder.RELEVANCE, filter)
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
