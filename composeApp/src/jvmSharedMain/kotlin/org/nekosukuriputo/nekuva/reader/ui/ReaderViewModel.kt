package org.nekosukuriputo.nekuva.reader.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
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

/**
 * Reader with inter-chapter navigation (subset of reader-advanced — see MIGRATION.md):
 * - Forward continuous-append: when the user nears the end of the loaded pages, the next
 *   chapter's pages are appended to the SAME list (each page keeps its chapterId), mirroring
 *   Doki's vertical/continuous behaviour.
 * - Explicit Next/Prev chapter controls (replace content with the target chapter at page 0).
 * - History moves with the active chapter via the existing [HistoryUpdateUseCase] path.
 * Backward continuous-prepend, branch selection and page-trimming stay deferred (MIGRATION.md).
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
        settings.defaultReaderMode = mode // remember the chosen mode for next time
    }

    /** Reactive page position (page x of N in the current chapter) for the info bar / page-number overlay. */
    private val _pageIndicator = MutableStateFlow(PageIndicator(0, 0, ""))
    val pageIndicator: StateFlow<PageIndicator> = _pageIndicator.asStateFlow()

    private var manga: Manga? = null
    private val mangaFlow = MutableStateFlow<Manga?>(null)
    private var chapters: List<MangaChapter> = emptyList()      // ordered (parser order)

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

    // Scroll signalling: the screen only (re)scrolls when scrollToken changes.
    private var scrollToken: Int = 0
    private var scrollTarget: Int = 0

    private val appendMutex = Mutex()
    private var appendingChapterId: Long? = null
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
                chapters = m.chapters ?: emptyList()
                val chapter = chapters.find { it.id == initialChapterId }
                    ?: throw IllegalArgumentException("Chapter with ID $initialChapterId not found.")

                val pages = fetchPages(m, chapter)
                loadedPages = pages.mapIndexed { i, p -> LoadedPage(p, chapter.id, i) }
                currentChapterId = chapter.id

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
        return if (m.source.name == "LOCAL") {
            localMangaRepository.getPages(chapter)
        } else {
            val source = MangaParserSource.entries.find { it.name == m.source.name }
                ?: throw IllegalArgumentException("Unknown source: ${m.source.name}")
            repositoryFactory.create(source).getPages(chapter)
        }
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
            emitSuccess()
        }
        // Auto-advance: near the end of the loaded list -> append the next chapter.
        if (index >= loadedPages.lastIndex - END_THRESHOLD) {
            appendNextChapter()
        }
    }

    private fun writeHistory(lp: LoadedPage) {
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
                    emitSuccess()
                }
            } catch (_: Exception) {
                // Appending failed (e.g. stubbed JS / network); the explicit Next button still works.
            } finally {
                appendingChapterId = null
            }
        }
    }

    /** Explicit Next/Prev chapter control: replace the content with the target chapter at page 0. */
    fun goToChapter(delta: Int) {
        val idx = chapters.indexOfFirst { it.id == currentChapterId }
        if (idx < 0) return
        loadChapterAt(chapters.getOrNull(idx + delta) ?: return, 0)
    }

    /** Open a specific chapter (from the reader chapter list) at page 0. */
    fun goToChapterById(chapterId: Long) {
        loadChapterAt(chapters.firstOrNull { it.id == chapterId } ?: return, 0)
    }

    /** Open a specific chapter at a given page (from the bookmarks tab). */
    fun goToChapterAtPage(chapterId: Long, page: Int) {
        if (chapterId == currentChapterId) {
            jumpToPageIndex(loadedPages.indexOfFirst { it.chapterId == chapterId && it.pageInChapter == page }.takeIf { it >= 0 } ?: page)
        } else {
            loadChapterAt(chapters.firstOrNull { it.id == chapterId } ?: return, page)
        }
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
                }
            } catch (e: Exception) {
                println("[Nekuva][bookmark] toggle failed: ${e.message}")
            }
        }
    }

    private fun emitSuccess() {
        val m = manga ?: return
        val idx = chapters.indexOfFirst { it.id == currentChapterId }
        val chapter = chapters.getOrNull(idx)
        _uiState.value = ReaderUiState.Success(
            manga = m,
            pages = loadedPages,
            currentChapterId = currentChapterId,
            currentChapterName = chapter?.title?.takeIf { it.isNotEmpty() } ?: chapter?.name ?: "",
            currentChapterIndex = idx,
            chaptersTotal = chapters.size,
            chapters = chapters.mapIndexed { i, c ->
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
        )
    }

    private companion object {
        const val END_THRESHOLD = 2
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
    ) : ReaderUiState
    data class Error(val exception: Throwable) : ReaderUiState
}
