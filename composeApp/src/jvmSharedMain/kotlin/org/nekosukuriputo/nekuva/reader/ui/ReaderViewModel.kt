package org.nekosukuriputo.nekuva.reader.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.mp.KoinPlatform
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.bookmarks.domain.BookmarksRepository
import org.nekosukuriputo.nekuva.core.nav.ReaderRoute
import org.nekosukuriputo.nekuva.core.model.isNsfw
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.parser.ParserMangaRepository
import org.nekosukuriputo.nekuva.parsers.config.ConfigKey
import org.nekosukuriputo.nekuva.history.data.HistoryRepository
import org.nekosukuriputo.nekuva.history.domain.HistoryUpdateUseCase
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A single page in the continuous reader list, tagged with the chapter it belongs to. */
data class LoadedPage(
    val page: MangaPage,
    val chapterId: Long,
    val pageInChapter: Int,
)

/** Current page position within the active chapter (drives the reader info bar / page-number overlay). */
data class PageIndicator(
    val page: Int,
    val total: Int,
    val chapterName: String,
)

/** A chapter entry for the reader's chapter-list sheet. */
data class ReaderChapterItem(
    val id: Long,
    val number: Int,
    val name: String,
    val isCurrent: Boolean,
)

/** A translation/scanlation branch for the reader's branch selector (Doki's MangaBranch). */
data class ReaderBranch(
    val name: String?,
    val count: Int,
)

/**
 * Preferred image-server / mirror choice for the current source (Doki's ImageServerDelegate). Present
 * only for sources that expose a `ConfigKey.PreferredImageServer`; [options] is a list of
 * (value, label) pairs and [current] is the selected value (an empty label means "Automatic").
 */
data class ImageServerUiState(
    val options: List<Pair<String, String>>,
    val current: String?,
)

/** Transient reader toast (Doki's ReaderToastView): bookmark add/remove + chapter change. */
sealed interface ReaderToast {
    data object BookmarkAdded : ReaderToast
    data object BookmarkRemoved : ReaderToast
    data class Chapter(val name: String) : ReaderToast
    data object Incognito : ReaderToast // forced incognito (opened from a bookmark)
}

/**
 * Reader with continuous inter-chapter navigation (Doki's `onCurrentPageChanged` boundary loading):
 * - Forward append: when the LAST visible page nears the end, the next chapter's pages are appended
 *   to the SAME list (each page keeps its chapterId).
 * - Backward prepend (webtoon): when the FIRST visible page nears the start, the previous chapter's
 *   pages are prepended; stable LazyColumn item keys keep the reading position (no scroll jump).
 * - Explicit Next/Prev chapter controls (replace content with the target chapter at page 0).
 * - Navigation always uses the FULL chapter list (not branch-filtered) so inconsistent per-chapter
 *   branch values can't strand the reader; the branch selector only filters the chapters-sheet view.
 * Page-trimming (Doki's PAGES_TRIM_THRESHOLD) stays deferred (MIGRATION.md).
 */
@OptIn(ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val mangaDataRepository: MangaDataRepository,
    private val repositoryFactory: MangaRepository.Factory,
    private val localMangaRepository: LocalMangaRepository,
    private val historyUpdateUseCase: HistoryUpdateUseCase,
    private val bookmarksRepository: BookmarksRepository,
    private val settings: org.nekosukuriputo.nekuva.core.prefs.AppSettings,
    private val pageSaveHelper: org.nekosukuriputo.nekuva.reader.domain.PageSaveHelper,
    private val scrobblerManager: org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerManager,
    private val detectReaderModeUseCase: org.nekosukuriputo.nekuva.reader.domain.DetectReaderModeUseCase,
    private val discordRpcManager: org.nekosukuriputo.nekuva.scrobbling.discord.DiscordRpcManager,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReaderRoute>()
    private val mangaId = route.mangaId
    private val initialChapterId = route.chapterId

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Active reading mode (initialised from the default, switchable from the reader menu). */
    private val _readerMode = MutableStateFlow(settings.defaultReaderMode)
    val readerMode: StateFlow<org.nekosukuriputo.nekuva.core.prefs.ReaderMode> = _readerMode.asStateFlow()

    fun setReaderMode(mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode) {
        _readerMode.value = mode
        settings.defaultReaderMode = mode // remember as the global default
        // Also persist per-manga so it sticks for this title (and overrides auto-detect next time).
        manga?.let { m -> viewModelScope.launch { runCatching { mangaDataRepository.saveReaderMode(m, mode) } } }
    }

    /** Reactive page position (page x of N in the current chapter) for the info bar / page-number overlay. */
    private val _pageIndicator = MutableStateFlow(PageIndicator(0, 0, ""))
    val pageIndicator: StateFlow<PageIndicator> = _pageIndicator.asStateFlow()

    private var manga: Manga? = null
    private val mangaFlow = MutableStateFlow<Manga?>(null)
    private var allChapters: List<MangaChapter> = emptyList()    // every branch (parser order)
    private var selectedBranch: String? = null                  // branch currently shown/navigated
    private var chapters: List<MangaChapter> = emptyList()      // allChapters filtered to selectedBranch

    /** Bookmarks of the current manga (for the reader chapters sheet's bookmarks tab). */
    val bookmarks: StateFlow<List<Bookmark>> = mangaFlow
        .flatMapLatest { m -> if (m == null) flowOf(emptyList()) else bookmarksRepository.observeBookmarks(m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var loadedPages: List<LoadedPage> = emptyList()     // continuous, multi-chapter
    private var currentChapterId: Long = 0L                     // chapter of the visible page
    private var currentVisibleIndex: Int = 0                    // index into loadedPages

    // Reactive bookmark state of the currently visible page (drives the toolbar bookmark icon).
    private val bookmarkKey = MutableStateFlow<Triple<Manga, Long, Int>?>(null)
    val isBookmarked: StateFlow<Boolean> = bookmarkKey
        .flatMapLatest { key ->
            if (key == null) flowOf(false)
            else bookmarksRepository.observeBookmark(key.first, key.second, key.third).map { it != null }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Effective reader colour filter: per-manga override if set, else the global one (Doki parity). */
    val colorFilter: StateFlow<org.nekosukuriputo.nekuva.reader.domain.ReaderColorFilter> = mangaFlow
        .flatMapLatest { m ->
            if (m == null) flowOf(settings.readerColorFilter)
            else mangaDataRepository.observeColorFilter(m.id)
                .map { perManga -> perManga?.takeUnless { it.isEmpty } ?: settings.readerColorFilter }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.readerColorFilter)

    /** Live-update the per-manga colour filter from the in-reader colour panel. */
    fun setColorFilter(filter: org.nekosukuriputo.nekuva.reader.domain.ReaderColorFilter) {
        val m = manga ?: return
        viewModelScope.launch {
            runCatching { mangaDataRepository.saveColorFilter(m, filter.takeUnless { it.isEmpty }) }
        }
    }

    /** Seek to a 0-based page within the currently visible chapter (driven by the bottom slider). */
    fun seekToPageInCurrentChapter(pageInChapter: Int) {
        val idx = loadedPages.indexOfFirst { it.chapterId == currentChapterId && it.pageInChapter == pageInChapter }
        if (idx >= 0) jumpToPageIndex(idx)
    }

    /** Which controls to render in the bottom bar (Doki's reader_controls preference). */
    val readerControls: Set<org.nekosukuriputo.nekuva.core.prefs.ReaderControl> get() = settings.readerControls

    // Preferred image server / mirror (Doki's ImageServerDelegate) — null when the source has no such config.
    private val _imageServer = MutableStateFlow<ImageServerUiState?>(null)
    val imageServer: StateFlow<ImageServerUiState?> = _imageServer.asStateFlow()

    /** Resolve the parser repository for the current manga (for source-config access). */
    private fun parserRepository(): ParserMangaRepository? {
        val m = manga ?: return null
        val source = MangaParserSource.entries.find { it.name == m.source.name } ?: return null
        return repositoryFactory.create(source) as? ParserMangaRepository
    }

    /** Re-read the source's PreferredImageServer config into [imageServer] (off the main thread). */
    private fun refreshImageServerState() {
        viewModelScope.launch {
            _imageServer.value = runCatching {
                withContext(Dispatchers.Default) {
                    val repo = parserRepository() ?: return@withContext null
                    val key = repo.getConfigKeys()
                        .firstNotNullOfOrNull { it as? ConfigKey.PreferredImageServer } ?: return@withContext null
                    // presetValues is Map<entryValue?, label?>; a null entryValue is Doki's "automatic"
                    // (stored as "" -> resolves to the source default). Coalesce both to "" here.
                    val options = ArrayList<Pair<String, String>>()
                    for (entry in key.presetValues) {
                        options.add((entry.key ?: "") to (entry.value ?: ""))
                    }
                    ImageServerUiState(
                        options = options,
                        current = repo.getConfig()[key],
                    )
                }
            }.getOrNull()
        }
    }

    /** Switch the preferred image server (Doki): persist, drop cached page urls, reload the chapter. */
    fun setImageServer(value: String) {
        viewModelScope.launch {
            val changed = runCatching {
                withContext(Dispatchers.Default) {
                    val repo = parserRepository() ?: return@withContext false
                    val key = repo.getConfigKeys()
                        .firstNotNullOfOrNull { it as? ConfigKey.PreferredImageServer } ?: return@withContext false
                    if (repo.getConfig()[key] == value) return@withContext false
                    repo.getConfig()[key] = value
                    repo.invalidateCache()
                    true
                }
            }.getOrDefault(false)
            if (changed) {
                refreshImageServerState()
                reloadCurrentChapter()
            }
        }
    }

    /** Reload the current chapter (after a config change) keeping the current page position. */
    private fun reloadCurrentChapter() {
        val target = allChapters.firstOrNull { it.id == currentChapterId } ?: return
        val page = loadedPages.getOrNull(currentVisibleIndex)?.pageInChapter ?: 0
        loadChapterAt(target, page)
    }

    // One-shot result of a save-page action: the saved location, or null on failure (drives a snackbar).
    private val _pageSaveEvent = kotlinx.coroutines.flow.MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val pageSaveEvent: kotlinx.coroutines.flow.SharedFlow<String?> = _pageSaveEvent.asSharedFlow()

    // Incognito: when on, history/stats are not written and progress isn't scrobbled (Doki parity).
    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    // Asks the UI to prompt for incognito on an NSFW manga when the pref is ASK.
    private val _askNsfwIncognito = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val askNsfwIncognito: kotlinx.coroutines.flow.SharedFlow<Unit> = _askNsfwIncognito.asSharedFlow()

    private var lastScrobbledChapterId = 0L

    // Transient reader toasts (bookmark add/remove, chapter change) — Doki's ReaderToastView.
    private val _toast = kotlinx.coroutines.flow.MutableSharedFlow<ReaderToast>(extraBufferCapacity = 4)
    val toast: kotlinx.coroutines.flow.SharedFlow<ReaderToast> = _toast.asSharedFlow()

    private fun emitChapterToast(chapterId: Long) {
        if (!settings.isReaderChapterToastEnabled) return
        val c = chapters.firstOrNull { it.id == chapterId } ?: allChapters.firstOrNull { it.id == chapterId } ?: return
        val name = c.title?.takeIf { it.isNotEmpty() } ?: c.name ?: return
        _toast.tryEmit(ReaderToast.Chapter(name))
    }

    /** Decide incognito for this session from the global/NSFW settings (Doki's logic). */
    private fun resolveIncognito(m: Manga) {
        val nsfw = m.isNsfw()
        when {
            settings.isIncognitoModeEnabled -> _isIncognito.value = true
            nsfw && settings.incognitoModeForNsfw == org.nekosukuriputo.nekuva.core.prefs.TriStateOption.ENABLED ->
                _isIncognito.value = true
            nsfw && settings.incognitoModeForNsfw == org.nekosukuriputo.nekuva.core.prefs.TriStateOption.ASK ->
                _askNsfwIncognito.tryEmit(Unit)
            else -> _isIncognito.value = false
        }
    }

    /** Answer the NSFW incognito prompt; [dontAskAgain] persists the choice (Doki's setIncognitoMode). */
    fun setIncognito(value: Boolean, dontAskAgain: Boolean) {
        _isIncognito.value = value
        if (dontAskAgain) {
            settings.incognitoModeForNsfw = if (value) {
                org.nekosukuriputo.nekuva.core.prefs.TriStateOption.ENABLED
            } else {
                org.nekosukuriputo.nekuva.core.prefs.TriStateOption.DISABLED
            }
        }
    }

    fun toggleIncognito() {
        _isIncognito.value = !_isIncognito.value
    }

    /** Push read progress to linked scrobblers when the read chapter changes (skipped in incognito). */
    private fun maybeScrobble(chapterId: Long) {
        if (_isIncognito.value || chapterId == lastScrobbledChapterId) return
        lastScrobbledChapterId = chapterId
        val m = manga ?: return
        viewModelScope.launch { runCatching { scrobblerManager.scrobble(m, chapterId) } }
    }

    /** Update Discord Rich Presence for the read chapter (Doki DiscordRpc; skipped in incognito). */
    private fun updateDiscordRpc(chapterId: Long) {
        if (_isIncognito.value) return
        val m = manga ?: return
        val number = chapters.indexOfFirst { it.id == chapterId }.let { if (it >= 0) it + 1 else 1 }
        runCatching { discordRpcManager.updateRpc(m, number, chapters.size) }
    }

    override fun onCleared() {
        // Tear down the Discord presence when leaving the reader (Doki DiscordRpc.onCleared).
        runCatching { discordRpcManager.clearRpc() }
        super.onCleared()
    }

    /** Save the currently visible page to platform storage (Doki's "save page"). */
    fun savePage() {
        val lp = loadedPages.getOrNull(currentVisibleIndex) ?: return
        viewModelScope.launch {
            val result = runCatching { pageSaveHelper.save(lp.page) }
                .onFailure { println("[Nekuva][reader] save page failed: ${it.message}") }
                .getOrNull()
            _pageSaveEvent.tryEmit(result)
        }
    }

    /** Share the currently visible page via the platform share mechanism. */
    fun sharePage() {
        val lp = loadedPages.getOrNull(currentVisibleIndex) ?: return
        viewModelScope.launch {
            val result = runCatching { pageSaveHelper.share(lp.page) }
                .onFailure { println("[Nekuva][reader] share page failed: ${it.message}") }
                .getOrNull()
            _pageSaveEvent.tryEmit(result)
        }
    }

    // Scroll signalling: the screen only (re)scrolls when scrollToken changes.
    private var scrollToken: Int = 0
    private var scrollTarget: Int = 0

    private val appendMutex = Mutex()
    private var appendingChapterId: Long? = null
    private var prependingChapterId: Long? = null
    private var navJob: Job? = null

    init {
        loadInitial()
    }

    fun retry() = loadInitial()

    private fun loadInitial() {
        navJob?.cancel()
        navJob = viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                val m = mangaDataRepository.findMangaById(mangaId, withChapters = true)
                    ?: throw IllegalArgumentException("Manga with ID $mangaId not found.")
                manga = m
                mangaFlow.value = m
                resolveIncognito(m)
                // Opened from a bookmark: force incognito (Doki) so this peek doesn't touch history.
                if (route.incognito && !_isIncognito.value) {
                    _isIncognito.value = true
                    _toast.tryEmit(ReaderToast.Incognito)
                }
                refreshImageServerState()
                allChapters = m.chapters ?: emptyList()
                val chapter = allChapters.find { it.id == initialChapterId }
                    ?: throw IllegalArgumentException("Chapter with ID $initialChapterId not found.")
                // Default the branch SELECTOR to the opened chapter's branch. Navigation (append +
                // prev/next) always uses the full ordered list so inconsistent per-chapter branch
                // values from a source can't strand the reader (see #19 regression).
                selectedBranch = chapter.branch
                chapters = allChapters

                val pages = fetchPages(m, chapter)
                // Auto-detect the reading mode (Doki): per-manga saved mode, else webtoon-by-aspect-ratio.
                runCatching { detectReaderModeUseCase(m, pages) }.getOrNull()?.let { _readerMode.value = it }
                loadedPages = pages.mapIndexed { i, p -> LoadedPage(p, chapter.id, i) }
                currentChapterId = chapter.id
                maybeScrobble(chapter.id)
                updateDiscordRpc(chapter.id)

                // Resume target: an explicit page (e.g. opened from a bookmark) overrides history.
                val maxIndex = (loadedPages.size - 1).coerceAtLeast(0)
                val resumePage = if (route.page >= 0) {
                    route.page.coerceIn(0, maxIndex)
                } else {
                    val history = KoinPlatform.getKoin().get<HistoryRepository>().getOne(m)
                    if (history != null && history.chapterId == chapter.id) {
                        history.page.coerceIn(0, maxIndex)
                    } else {
                        0
                    }
                }

                scrollTarget = resumePage
                scrollToken++
                emitSuccess()
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e)
            }
        }
    }

    private suspend fun fetchPages(m: Manga, chapter: MangaChapter): List<MangaPage> {
        // Offline-first (Doki parity): read the chapter from disk if it's downloaded — even for a
        // remote manga where only some chapters are downloaded — else fetch online.
        localMangaRepository.getPagesIfDownloaded(m, chapter)?.let { return it }
        val source = MangaParserSource.entries.find { it.name == m.source.name }
            ?: throw IllegalArgumentException("Unknown source: ${m.source.name}")
        return repositoryFactory.create(source).getPages(chapter)
    }

    /** Called as the user scrolls; [index] is the first visible page index in [loadedPages]. */
    fun onVisibleIndexChanged(index: Int) {
        val lp = loadedPages.getOrNull(index) ?: return
        currentVisibleIndex = index
        manga?.let { bookmarkKey.value = Triple(it, lp.chapterId, lp.pageInChapter) }
        run {
            val total = loadedPages.count { it.chapterId == lp.chapterId }
            val chapterName = chapters.firstOrNull { it.id == lp.chapterId }
                ?.let { c -> c.title?.takeIf { it.isNotEmpty() } ?: c.name } ?: ""
            _pageIndicator.value = PageIndicator(lp.pageInChapter + 1, total, chapterName)
        }
        writeHistory(lp)
        if (lp.chapterId != currentChapterId) {
            // The visible page crossed into another (already-appended) chapter — update the toolbar
            // without moving the scroll position (scrollToken unchanged).
            currentChapterId = lp.chapterId
            maybeScrobble(lp.chapterId)
            updateDiscordRpc(lp.chapterId)
            emitChapterToast(lp.chapterId)
            emitSuccess()
        }
    }

    /**
     * Continuous-reader boundary loading (Doki's `onCurrentPageChanged`): append the next chapter when
     * the LAST visible page nears the end, and (webtoon only) prepend the previous chapter when the
     * FIRST visible page nears the start. Using the last/first visible index — not just the top one —
     * is what makes the trigger fire reliably.
     */
    fun onVisibleBounds(firstVisible: Int, lastVisible: Int, allowPrepend: Boolean) {
        if (lastVisible >= loadedPages.lastIndex - END_THRESHOLD) {
            appendNextChapter()
        }
        if (allowPrepend && firstVisible <= END_THRESHOLD) {
            prependPrevChapter()
        }
    }

    private fun writeHistory(lp: LoadedPage) {
        if (_isIncognito.value) return // incognito: don't record reading history
        val m = manga ?: return
        val chapterPageCount = loadedPages.count { it.chapterId == lp.chapterId }.coerceAtLeast(1)
        val percent = (lp.pageInChapter + 1) / chapterPageCount.toFloat()
        historyUpdateUseCase.invokeAsync(
            manga = m,
            chapterId = lp.chapterId,
            page = lp.pageInChapter,
            scroll = 0,
            percent = percent,
        )
    }

    private fun appendNextChapter() {
        val lastId = loadedPages.lastOrNull()?.chapterId ?: return
        val idx = chapters.indexOfFirst { it.id == lastId }
        if (idx < 0) return
        val next = chapters.getOrNull(idx + 1) ?: return        // last chapter -> nothing to append
        if (appendingChapterId == next.id) return               // already in flight
        if (loadedPages.any { it.chapterId == next.id }) return  // already appended
        appendingChapterId = next.id
        viewModelScope.launch {
            try {
                appendMutex.withLock {
                    if (loadedPages.any { it.chapterId == next.id }) return@withLock
                    val m = manga ?: return@withLock
                    val pages = fetchPages(m, next)
                    val wrapped = pages.mapIndexed { i, p -> LoadedPage(p, next.id, i) }
                    // Append only — existing indices/scroll stay put.
                    loadedPages = loadedPages + wrapped
                    trimLoadedPages(fromFront = true)
                    emitSuccess()
                }
            } catch (_: Exception) {
                // Appending failed (e.g. stubbed JS / network); the explicit Next button still works.
            } finally {
                appendingChapterId = null
            }
        }
    }

    /**
     * Prepend the previous chapter to the FRONT of the continuous list (webtoon). The LazyColumn keeps
     * the user's position because items have stable keys, so this is seamless (Doki's addFirst).
     */
    private fun prependPrevChapter() {
        val firstId = loadedPages.firstOrNull()?.chapterId ?: return
        val idx = chapters.indexOfFirst { it.id == firstId }
        if (idx <= 0) return                                     // already at the first chapter
        val prev = chapters.getOrNull(idx - 1) ?: return
        if (prependingChapterId == prev.id) return               // already in flight
        if (loadedPages.any { it.chapterId == prev.id }) return  // already prepended
        prependingChapterId = prev.id
        viewModelScope.launch {
            try {
                appendMutex.withLock {
                    if (loadedPages.any { it.chapterId == prev.id }) return@withLock
                    val m = manga ?: return@withLock
                    val pages = fetchPages(m, prev)
                    val wrapped = pages.mapIndexed { i, p -> LoadedPage(p, prev.id, i) }
                    // Prepend — stable item keys keep the current page in view (no scroll jump).
                    loadedPages = wrapped + loadedPages
                    trimLoadedPages(fromFront = false)
                    emitSuccess()
                }
            } catch (_: Exception) {
                // Prepend failed (network/JS); the explicit Prev button still works.
            } finally {
                prependingChapterId = null
            }
        }
    }

    /**
     * Memory cap (Doki's `PAGES_TRIM_THRESHOLD`): once the continuous list spans more than one chapter
     * and grows past the threshold, drop the far-away chapter. **Webtoon only** — the LazyColumn's stable
     * item keys keep the visible page in place when items are removed (same mechanism as prepend). Paged
     * modes use a Pager whose index would jump on a front-removal, so trimming there is intentionally
     * skipped. The chapter currently being read is never trimmed.
     */
    private fun trimLoadedPages(fromFront: Boolean) {
        if (_readerMode.value != org.nekosukuriputo.nekuva.core.prefs.ReaderMode.WEBTOON) return
        if (loadedPages.size <= PAGES_TRIM_THRESHOLD) return
        val distinctChapters = loadedPages.map { it.chapterId }.distinct()
        if (distinctChapters.size <= 1) return
        val victim = if (fromFront) distinctChapters.first() else distinctChapters.last()
        if (victim == currentChapterId) return
        val removed = loadedPages.count { it.chapterId == victim }
        loadedPages = loadedPages.filterNot { it.chapterId == victim }
        if (fromFront) currentVisibleIndex = (currentVisibleIndex - removed).coerceAtLeast(0)
    }

    /** Explicit Next/Prev chapter control: replace the content with the target chapter at page 0. */
    fun goToChapter(delta: Int) {
        val idx = chapters.indexOfFirst { it.id == currentChapterId }
        if (idx < 0) return
        loadChapterAt(chapters.getOrNull(idx + delta) ?: return, 0)
    }

    /** Open a specific chapter (from the reader chapter list) at page 0. */
    fun goToChapterById(chapterId: Long) {
        val target = allChapters.firstOrNull { it.id == chapterId } ?: return
        switchBranchTo(target.branch)
        loadChapterAt(target, 0)
    }

    /** Open a specific chapter at a given page (from the bookmarks tab — may be any branch). */
    fun goToChapterAtPage(chapterId: Long, page: Int) {
        if (chapterId == currentChapterId) {
            jumpToPageIndex(loadedPages.indexOfFirst { it.chapterId == chapterId && it.pageInChapter == page }.takeIf { it >= 0 } ?: page)
        } else {
            val target = allChapters.firstOrNull { it.id == chapterId } ?: return
            switchBranchTo(target.branch)
            loadChapterAt(target, page)
        }
    }

    /** Switch the browsed/translation branch (Doki's branch selector) — filters the sheet display only. */
    fun setBranch(branch: String?) {
        if (branch == selectedBranch) return
        selectedBranch = branch
        emitSuccess()
    }

    private fun switchBranchTo(branch: String?) {
        selectedBranch = branch
    }

    /** Distinct branches with chapter counts; empty when the manga has only one (no selector shown). */
    private fun computeBranches(): List<ReaderBranch> {
        val grouped = allChapters.groupBy { it.branch }
        return if (grouped.size <= 1) emptyList() else grouped.map { ReaderBranch(it.key, it.value.size) }
    }

    /** Jump to a page within the currently loaded pages (from the pages-grid tab). */
    fun jumpToPageIndex(index: Int) {
        if (loadedPages.isEmpty()) return
        scrollTarget = index.coerceIn(0, loadedPages.lastIndex)
        scrollToken++
        emitSuccess()
    }

    private fun loadChapterAt(target: MangaChapter, page: Int) {
        navJob?.cancel()
        navJob = viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                val m = manga ?: return@launch
                val pages = fetchPages(m, target)
                loadedPages = pages.mapIndexed { i, p -> LoadedPage(p, target.id, i) }
                currentChapterId = target.id
                maybeScrobble(target.id)
                emitChapterToast(target.id)
                val resume = page.coerceIn(0, (loadedPages.size - 1).coerceAtLeast(0))
                loadedPages.getOrNull(resume)?.let { writeHistory(it) }
                scrollTarget = resume
                scrollToken++
                emitSuccess()
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e)
            }
        }
    }

    /** Toolbar action: bookmark/un-bookmark the currently visible page. */
    fun toggleBookmark() {
        val lp = loadedPages.getOrNull(currentVisibleIndex) ?: return
        val m = manga ?: return
        viewModelScope.launch {
            try {
                if (isBookmarked.value) {
                    bookmarksRepository.removeBookmark(m.id, lp.chapterId, lp.pageInChapter)
                    _toast.tryEmit(ReaderToast.BookmarkRemoved)
                } else {
                    val chapterPageCount = loadedPages.count { it.chapterId == lp.chapterId }.coerceAtLeast(1)
                    val percent = (lp.pageInChapter + 1) / chapterPageCount.toFloat()
                    bookmarksRepository.addBookmark(
                        Bookmark(
                            manga = m,
                            pageId = lp.page.id,
                            chapterId = lp.chapterId,
                            page = lp.pageInChapter,
                            scroll = 0,
                            imageUrl = lp.page.preview?.takeIf { it.isNotEmpty() } ?: lp.page.url,
                            createdAt = Clock.System.now().toEpochMilliseconds(),
                            percent = percent,
                        ),
                    )
                    _toast.tryEmit(ReaderToast.BookmarkAdded)
                }
            } catch (e: Exception) {
                println("[Nekuva][bookmark] toggle failed: ${e.message}")
            }
        }
    }

    private fun emitSuccess() {
        val m = manga ?: return
        // Navigation/index use the full ordered list; the sheet shows only the selected branch.
        val idx = chapters.indexOfFirst { it.id == currentChapterId }
        val chapter = chapters.getOrNull(idx)
        val branches = computeBranches()
        val sheetChapters = if (branches.isEmpty()) chapters else chapters.filter { it.branch == selectedBranch }
        _uiState.value = ReaderUiState.Success(
            manga = m,
            pages = loadedPages,
            currentChapterId = currentChapterId,
            currentChapterName = chapter?.title?.takeIf { it.isNotEmpty() } ?: chapter?.name ?: "",
            currentChapterIndex = idx,
            chaptersTotal = chapters.size,
            chapters = sheetChapters.mapIndexed { i, c ->
                ReaderChapterItem(
                    id = c.id,
                    number = i + 1,
                    name = c.title?.takeIf { it.isNotEmpty() } ?: c.name ?: "",
                    isCurrent = c.id == currentChapterId,
                )
            },
            hasPrev = idx > 0,
            hasNext = idx in 0 until chapters.lastIndex,
            scrollToIndex = scrollTarget.coerceAtLeast(0),
            scrollToken = scrollToken,
            branches = branches,
            selectedBranch = selectedBranch,
        )
    }

    private companion object {
        const val END_THRESHOLD = 2
        const val PAGES_TRIM_THRESHOLD = 120 // Doki parity: cap continuous-list pages (webtoon)
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Success(
        val manga: Manga,
        val pages: List<LoadedPage>,
        val currentChapterId: Long,
        val currentChapterName: String,
        val currentChapterIndex: Int,
        val chaptersTotal: Int,
        val chapters: List<ReaderChapterItem>,
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val scrollToIndex: Int,
        val scrollToken: Int,
        val branches: List<ReaderBranch> = emptyList(),
        val selectedBranch: String? = null,
    ) : ReaderUiState
    data class Error(val exception: Throwable) : ReaderUiState
}
