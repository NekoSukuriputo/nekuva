package org.nekosukuriputo.nekuva.details.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.nav.MangaDetailsRoute
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.core.model.MangaOverride
import org.nekosukuriputo.nekuva.core.model.withOverride
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.bookmarks.domain.BookmarksRepository
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val favouritesRepository: FavouritesRepository,
    private val historyRepository: org.nekosukuriputo.nekuva.history.data.HistoryRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val localMangaRepository: org.nekosukuriputo.nekuva.local.data.LocalMangaRepository,
    private val settings: org.nekosukuriputo.nekuva.core.prefs.AppSettings,
    private val statsRepository: org.nekosukuriputo.nekuva.stats.data.StatsRepository,
    private val downloadManager: org.nekosukuriputo.nekuva.download.domain.DownloadManager,
    private val scrobblerManager: org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerManager,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MangaDetailsRoute>()
    private val mangaId = route.mangaId

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val loadedManga = MutableStateFlow<Manga?>(null)

    /**
     * Chapter ids already saved on disk (Doki: chapter list shows an SD-card badge for downloaded chapters).
     * Recomputed whenever the manga loads or any download finishes.
     */
    val downloadedChapterIds: StateFlow<Set<Long>> =
        kotlinx.coroutines.flow.combine(loadedManga.filterNotNull(), downloadManager.downloads) { m, _ -> m }
            .mapLatest { m ->
                runCatching {
                    localMangaRepository.findSavedManga(m, withDetails = true)?.manga?.chapters
                        ?.mapTo(HashSet()) { it.id }
                }.getOrNull() ?: emptySet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Download a single chapter (Doki per-chapter download from the chapters list). */
    fun downloadChapter(chapter: org.nekosukuriputo.nekuva.parsers.model.MangaChapter) {
        val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return
        viewModelScope.launch {
            runCatching {
                downloadManager.schedule(
                    listOf(
                        org.nekosukuriputo.nekuva.download.domain.DownloadTask(
                            manga = m, chaptersIds = setOf(chapter.id), destination = null, format = null, startPaused = false,
                        ),
                    ),
                )
            }
        }
    }

    /** Batch-download the selected chapters (Doki chapters ActionMode → save). */
    fun downloadChapters(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return
        viewModelScope.launch {
            runCatching {
                downloadManager.schedule(
                    listOf(
                        org.nekosukuriputo.nekuva.download.domain.DownloadTask(
                            manga = m, chaptersIds = ids, destination = null, format = null, startPaused = false,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Mark the selected chapters as read (Doki action_mark_current): set history to the furthest-along
     * selected chapter with full progress, so everything up to it counts as read.
     */
    fun markChaptersRead(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return
        val chapters = m.chapters ?: return
        val target = chapters.lastOrNull { it.id in ids } ?: return
        viewModelScope.launch {
            runCatching {
                historyRepository.addOrUpdate(manga = m, chapterId = target.id, page = 0, scroll = 0, percent = 1f, force = true)
            }
        }
    }

    /** Delete the selected downloaded chapters from storage (Doki chapters ActionMode → delete). */
    fun deleteChapters(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return
        viewModelScope.launch {
            runCatching {
                val saved = localMangaRepository.findSavedManga(m, withDetails = true)?.manga ?: m
                localMangaRepository.deleteChapters(saved, ids)
            }
        }
    }

    /** Current user override (custom title/cover) for this manga, for prefilling the edit dialog (CORE-7). */
    private val _override = MutableStateFlow<org.nekosukuriputo.nekuva.core.model.MangaOverride?>(null)
    val override: StateFlow<org.nekosukuriputo.nekuva.core.model.MangaOverride?> = _override.asStateFlow()

    /** Bookmarks of the currently opened manga (shown in the chapters bottom sheet). */
    val bookmarks: StateFlow<List<Bookmark>> = loadedManga.filterNotNull()
        .flatMapLatest { bookmarksRepository.observeBookmarks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isFavorite: StateFlow<Boolean> = favouritesRepository.observeCategoriesIds(mangaId)
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allCategories: StateFlow<List<FavouriteCategory>> = favouritesRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mangaCategories: StateFlow<Set<Long>> = favouritesRepository.observeCategoriesIds(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val history: StateFlow<org.nekosukuriputo.nekuva.core.model.MangaHistory?> = historyRepository.observeOne(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Scrobbling / tracking (Doki action_scrobbling + ScrobblingInfo cards) ---

    /** Authorized scrobblers the user can link this manga to (Doki availableScrobblers). */
    val availableScrobblers: List<org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler>
        get() = scrobblerManager.scrobblers.filter { it.isEnabled }

    /** Live tracking info for this manga across all authorized services (Doki scrobblingInfo). */
    val scrobblingInfo: StateFlow<List<org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingInfo>> =
        scrobblerManager.scrobblers
            .map { it.observeScrobblingInfo(mangaId) }
            .let { flows ->
                if (flows.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
                else kotlinx.coroutines.flow.combine(flows) { arr -> arr.filterNotNull() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Link this manga to a tracker entry, then seed its status from reading history (Doki onDoneClick). */
    fun linkScrobbler(
        scrobbler: org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler,
        target: org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerManga,
        onDone: () -> Unit = {},
    ) {
        val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return
        viewModelScope.launch {
            runCatching {
                val prev = scrobbler.getScrobblingInfoOrNull(mangaId)
                scrobbler.linkManga(mangaId, target.id)
                val h = historyRepository.getOne(m)
                scrobbler.updateScrobblingInfo(
                    mangaId = mangaId,
                    rating = prev?.rating ?: 0f,
                    status = prev?.status ?: when {
                        h == null -> org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus.PLANNED
                        h.percent >= 0.99f -> org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus.COMPLETED
                        else -> org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus.READING
                    },
                    comment = prev?.comment,
                )
                if (h != null) scrobbler.scrobble(m, h.chapterId)
            }
            onDone()
        }
    }

    /** Update tracking status/rating for a linked manga (Doki ScrobblingInfoSheet). */
    fun updateScrobbling(
        scrobbler: org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler,
        rating: Float,
        status: org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus,
    ) {
        viewModelScope.launch {
            runCatching {
                val prev = scrobbler.getScrobblingInfoOrNull(mangaId)
                scrobbler.updateScrobblingInfo(mangaId, rating, status, prev?.comment)
            }
        }
    }

    /** Unlink this manga from a tracker (Doki unregisterScrobbling). */
    fun unlinkScrobbler(scrobbler: org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler) {
        viewModelScope.launch { runCatching { scrobbler.unregisterScrobbling(mangaId) } }
    }

    /** Related manga (Doki RelatedMangaUseCase): fetched from the parser when related_manga is on. */
    private val _relatedManga = MutableStateFlow<List<Manga>>(emptyList())
    val relatedManga: StateFlow<List<Manga>> = _relatedManga.asStateFlow()

    /** Average seconds/page from stats (Doki getTimePerPage); 10 s default until stats accumulate. */
    private val secondsPerPage = MutableStateFlow(10)

    /** Estimated reading time (Doki ReadingTimeUseCase): null when off / too short / no chapters. */
    val readingTime: StateFlow<ReadingTimeInfo?> =
        kotlinx.coroutines.flow.combine(loadedManga, history, secondsPerPage) { m, h, spp ->
            if (m == null) null else computeReadingTime(m, h, spp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadDetails()
    }

    // Port of Doki's ReadingTimeUseCase: per-page time comes from stats (getTimePerPage), default 10 s.
    private fun computeReadingTime(
        manga: Manga,
        history: org.nekosukuriputo.nekuva.core.model.MangaHistory?,
        secondsPerPage: Int,
    ): ReadingTimeInfo? {
        if (!settings.prefBoolean(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_READING_TIME, true)) return null
        val chapters = manga.chapters
        if (chapters.isNullOrEmpty()) return null
        val isOnHistoryBranch = history != null && chapters.any { it.id == history.chapterId }
        var averageTimeSec = 20 /* pages */ * secondsPerPage.coerceAtLeast(1) * chapters.size
        if (isOnHistoryBranch && history != null) {
            averageTimeSec = (averageTimeSec * (1f - history.percent)).roundToInt()
        }
        if (averageTimeSec < 60) return null
        return ReadingTimeInfo(
            hours = averageTimeSec / 3600,
            minutes = (averageTimeSec / 60) % 60,
            isContinue = isOnHistoryBranch,
        )
    }

    private fun loadRelatedManga(manga: Manga) {
        if (manga.isLocal) return
        if (!settings.prefBoolean(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_RELATED_MANGA, true)) return
        viewModelScope.launch {
            runCatching {
                val source = MangaParserSource.entries.find { it.name == manga.source.name } ?: return@launch
                _relatedManga.value = repositoryFactory.create(source).getRelated(manga)
            }
        }
    }

    private fun loadDetails() {
        viewModelScope.launch { doLoadDetails(showLoading = true) }
    }

    /** Pull-to-refresh in progress (Doki): keeps the current content visible with a spinner on top. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Pull-to-refresh (Doki details swipe): re-fetch from the source WITHOUT clearing the shown content. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                doLoadDetails(showLoading = false)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun doLoadDetails(showLoading: Boolean) {
        if (showLoading) _uiState.value = DetailsUiState.Loading
        try {
            val base = mangaDataRepository.findMangaById(mangaId, withChapters = true)
                ?: run {
                    if (showLoading) {
                        _uiState.value = DetailsUiState.Error(
                            IllegalArgumentException("Manga with ID $mangaId not found in local cache."),
                        )
                    }
                    return
                }

            if (base.isLocal) {
                // Local manga read their chapters from the file.
                val local = localMangaRepository.getDetails(base)
                mangaDataRepository.storeManga(local, replaceExisting = true)
                finishLoad(local)
                return
            }

            val source = MangaParserSource.entries.find { it.name == base.source.name }
            // Offline-first (Doki): if this manga is downloaded/saved, show the saved copy immediately and
            // refresh from the source in the BACKGROUND — so it opens even if the source link changed /
            // was removed / there's no network (the cause of "Status=404" on saved manga).
            val saved = findSavedCopy(base)
            if (saved != null) {
                // replaceExisting = true so the downloaded copy's CHAPTERS reach the DB — the reader
                // resolves a downloaded chapter by id via findMangaById(withChapters), which otherwise
                // returns the chapter-less restored row ("Chapter with ID … not found").
                mangaDataRepository.storeManga(saved, replaceExisting = true)
                finishLoad(saved)
                if (source != null) {
                    val fresh = runCatching { repositoryFactory.create(source).getDetails(base) }.getOrNull()
                    if (fresh != null) {
                        mangaDataRepository.storeManga(fresh, replaceExisting = true)
                        finishLoad(fresh)
                    }
                }
                return
            }
            if (source != null) {
                // Not saved → try online. On failure (e.g. broken/removed source → 404) fall back to a
                // downloaded copy if one turns up, then to whatever chapters the DB cached, so a
                // downloaded manga still opens offline instead of erroring outright.
                val freshResult = runCatching { repositoryFactory.create(source).getDetails(base) }
                val fresh = freshResult.getOrNull()
                when {
                    fresh != null -> {
                        mangaDataRepository.storeManga(fresh, replaceExisting = true)
                        finishLoad(fresh)
                    }
                    else -> {
                        val savedFallback = findSavedCopy(base)
                        when {
                            savedFallback != null -> {
                                mangaDataRepository.storeManga(savedFallback, replaceExisting = true)
                                finishLoad(savedFallback)
                            }
                            !base.chapters.isNullOrEmpty() -> finishLoad(base)
                            // Only replace the screen with an error on the initial load — a failed refresh
                            // keeps the content that's already shown.
                            showLoading -> _uiState.value = DetailsUiState.Error(
                                freshResult.exceptionOrNull() ?: IllegalStateException("Source unavailable"),
                            )
                            else -> Unit
                        }
                    }
                }
            } else {
                // Unknown source, not saved → show whatever the DB cached.
                finishLoad(base)
            }
        } catch (e: Exception) {
            // A failed refresh keeps the visible content; only the initial load shows the error screen.
            if (showLoading || _uiState.value !is DetailsUiState.Success) _uiState.value = DetailsUiState.Error(e)
        }
    }

    /** The downloaded/saved local copy of [base] (with chapters from its index.json), or null. */
    private suspend fun findSavedCopy(base: Manga): Manga? = runCatching {
        localMangaRepository.findSavedManga(base, withDetails = true)?.manga
    }.getOrNull()

    /** Apply the user override, publish Success, and kick off related-manga + reading-time refinement. */
    private suspend fun finishLoad(manga: Manga) {
        val override = mangaDataRepository.getOverride(mangaId)
        _override.value = override
        val shown = manga.withOverride(override)
        _uiState.value = DetailsUiState.Success(shown)
        loadedManga.value = shown
        loadRelatedManga(shown)
        // Refine the reading-time estimate from recorded stats (Doki getTimePerPage), if any.
        if (settings.isStatsEnabled) {
            runCatching {
                val ms = statsRepository.getTimePerPage(mangaId)
                if (ms > 0L) secondsPerPage.value = (ms / 1000L).toInt().coerceAtLeast(1)
            }
        }
    }

    fun retry() {
        loadDetails()
    }

    /**
     * Save a custom title / cover override (Doki OverrideConfig). Blank fields clear that part of the
     * override (reverting to the source value). Reloads details so the new title/cover show immediately.
     */
    fun saveOverride(title: String?, coverUrl: String?) {
        viewModelScope.launch {
            val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return@launch
            val newOverride = MangaOverride(
                coverUrl = coverUrl?.trim()?.ifEmpty { null },
                title = title?.trim()?.ifEmpty { null },
                contentRating = _override.value?.contentRating, // keep any existing rating override
            )
            runCatching { mangaDataRepository.setOverride(m, newOverride) }
            loadDetails()
        }
    }

    /**
     * Set this manga's custom cover to one of its page images (Doki picker → OverrideConfig cover). Keeps any
     * existing title/content-rating override; reloads so the new cover shows. Mirrors picking a page as cover.
     */
    fun setCoverFromPage(coverUrl: String) {
        viewModelScope.launch {
            val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return@launch
            val existing = _override.value
            val newOverride = MangaOverride(
                coverUrl = coverUrl.trim().ifEmpty { null },
                title = existing?.title,
                contentRating = existing?.contentRating,
            )
            runCatching { mangaDataRepository.setOverride(m, newOverride) }
            loadDetails()
        }
    }

    /**
     * Open the online (remote) variant of a saved/local manga (Doki action_online): resolve the remote
     * manga from the local archive's source URL, store it, and hand the new id back for navigation.
     */
    fun openOnline(onResolved: (Long) -> Unit) {
        viewModelScope.launch {
            val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return@launch
            val remote = runCatching { localMangaRepository.getRemoteManga(m) }.getOrNull() ?: return@launch
            runCatching { mangaDataRepository.storeManga(remote, replaceExisting = false) }
            onResolved(remote.id)
        }
    }

    /** Delete a saved/local manga from storage (Doki action_delete); [onDone] runs after (e.g. pop back). */
    fun deleteLocal(onDone: () -> Unit) {
        viewModelScope.launch {
            val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return@launch
            runCatching { localMangaRepository.delete(m) }
            onDone()
        }
    }

    fun removeFromHistory() {
        viewModelScope.launch {
            val m = loadedManga.value ?: (uiState.value as? DetailsUiState.Success)?.manga ?: return@launch
            historyRepository.delete(m)
        }
    }

    // Details "Pages" tab (Doki pages_tab): page thumbnails of the current/first chapter, loaded once.
    private val _pagesState = MutableStateFlow<PagesPreviewState>(PagesPreviewState.Idle)
    val pagesState: StateFlow<PagesPreviewState> = _pagesState.asStateFlow()

    fun loadPagesPreview() {
        if (_pagesState.value != PagesPreviewState.Idle) return // load once per screen
        val m = loadedManga.value ?: return
        _pagesState.value = PagesPreviewState.Loading
        viewModelScope.launch {
            try {
                val chapters = m.chapters ?: emptyList()
                if (chapters.isEmpty()) {
                    _pagesState.value = PagesPreviewState.Empty
                    return@launch
                }
                // Doki previews the current (last-read) chapter, else the first.
                val targetId = history.value?.chapterId
                val chapter = chapters.firstOrNull { it.id == targetId } ?: chapters.first()
                val pages = localMangaRepository.getPagesIfDownloaded(m, chapter) ?: run {
                    val source = MangaParserSource.entries.find { it.name == m.source.name }
                        ?: throw IllegalStateException("Unknown source: ${m.source.name}")
                    val repo = repositoryFactory.create(source)
                    val raw = repo.getPages(chapter)
                    // Resolve the final image URL (Doki getPageUrl) for pages WITHOUT a thumbnail preview, so the
                    // grid loads on sources where MangaPage.url is an intermediate (e.g. blank pages otherwise).
                    coroutineScope {
                        val sem = Semaphore(4)
                        raw.map { p ->
                            async {
                                if (!p.preview.isNullOrEmpty() || !p.url.startsWith("http", ignoreCase = true)) {
                                    p
                                } else {
                                    sem.withPermit { runCatching { p.copy(url = repo.getPageUrl(p)) }.getOrDefault(p) }
                                }
                            }
                        }.awaitAll()
                    }
                }
                _pagesState.value = if (pages.isEmpty()) PagesPreviewState.Empty
                    else PagesPreviewState.Success(listOf(chapter to pages))
            } catch (e: Exception) {
                _pagesState.value = PagesPreviewState.Error(e)
            }
        }
    }

    fun loadAdjacentChapterPages(isNext: Boolean) {
        val currentState = _pagesState.value as? PagesPreviewState.Success ?: return
        if (isNext && currentState.isLoadingNext) return
        if (!isNext && currentState.isLoadingPrev) return

        val m = loadedManga.value ?: return
        val chapters = m.chapters ?: emptyList()
        
        val targetChapter = if (isNext) {
            val lastItem = currentState.items.last().first
            val idx = chapters.indexOfFirst { it.id == lastItem.id }
            if (idx >= 0) chapters.getOrNull(idx + 1) else null
        } else {
            val firstItem = currentState.items.first().first
            val idx = chapters.indexOfFirst { it.id == firstItem.id }
            if (idx >= 0) chapters.getOrNull(idx - 1) else null
        }
        
        if (targetChapter == null) return
        
        _pagesState.value = currentState.copy(isLoadingNext = isNext, isLoadingPrev = !isNext)
        viewModelScope.launch {
            try {
                val pages = localMangaRepository.getPagesIfDownloaded(m, targetChapter) ?: run {
                    val source = MangaParserSource.entries.find { it.name == m.source.name }
                        ?: throw IllegalStateException("Unknown source: ${m.source.name}")
                    val repo = repositoryFactory.create(source)
                    val raw = repo.getPages(targetChapter)
                    coroutineScope {
                        val sem = Semaphore(4)
                        raw.map { p ->
                            async {
                                if (!p.preview.isNullOrEmpty() || !p.url.startsWith("http", ignoreCase = true)) {
                                    p
                                } else {
                                    sem.withPermit { runCatching { p.copy(url = repo.getPageUrl(p)) }.getOrDefault(p) }
                                }
                            }
                        }.awaitAll()
                    }
                }
                
                if (pages.isNotEmpty()) {
                    val newPair = targetChapter to pages
                    _pagesState.value = if (isNext) {
                        currentState.copy(items = currentState.items + newPair, isLoadingNext = false, isLoadingPrev = false)
                    } else {
                        currentState.copy(items = listOf(newPair) + currentState.items, isLoadingNext = false, isLoadingPrev = false)
                    }
                } else {
                    _pagesState.value = currentState.copy(isLoadingNext = false, isLoadingPrev = false)
                }
            } catch (e: Exception) {
                _pagesState.value = currentState.copy(isLoadingNext = false, isLoadingPrev = false)
            }
        }
    }

    fun toggleCategory(categoryId: Long, isSelected: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state is DetailsUiState.Success) {
                if (isSelected) {
                    favouritesRepository.addToCategory(categoryId, listOf(state.manga))
                } else {
                    // Default (id 0) is a real category now — unchecking it removes from that category
                    // only, like any other category (not from all favourites).
                    favouritesRepository.removeFromCategory(categoryId, listOf(mangaId))
                }
            }
        }
    }
}

/** Estimated reading time (Doki ReadingTime), formatted in the Composable via string resources. */
data class ReadingTimeInfo(
    val hours: Int,
    val minutes: Int,
    val isContinue: Boolean,
)

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(val manga: Manga) : DetailsUiState
    data class Error(val exception: Throwable) : DetailsUiState
}

/** State of the Details "Pages" preview tab (Doki pages_tab). */
sealed interface PagesPreviewState {
    data object Idle : PagesPreviewState
    data object Loading : PagesPreviewState
    data object Empty : PagesPreviewState
    data class Success(
        val items: List<Pair<org.nekosukuriputo.nekuva.parsers.model.MangaChapter, List<org.nekosukuriputo.nekuva.parsers.model.MangaPage>>>,
        val isLoadingPrev: Boolean = false,
        val isLoadingNext: Boolean = false,
    ) : PagesPreviewState
    data class Error(val error: Throwable) : PagesPreviewState
}
