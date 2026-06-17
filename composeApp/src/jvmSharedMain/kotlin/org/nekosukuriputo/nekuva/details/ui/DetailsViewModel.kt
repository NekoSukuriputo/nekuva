package org.nekosukuriputo.nekuva.details.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.bookmarks.domain.BookmarksRepository
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MangaDetailsRoute>()
    private val mangaId = route.mangaId

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val loadedManga = MutableStateFlow<Manga?>(null)

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

    /** Related manga (Doki RelatedMangaUseCase): fetched from the parser when related_manga is on. */
    private val _relatedManga = MutableStateFlow<List<Manga>>(emptyList())
    val relatedManga: StateFlow<List<Manga>> = _relatedManga.asStateFlow()

    /** Estimated reading time (Doki ReadingTimeUseCase): null when off / too short / no chapters. */
    val readingTime: StateFlow<ReadingTimeInfo?> =
        kotlinx.coroutines.flow.combine(loadedManga, history) { m, h -> if (m == null) null else computeReadingTime(m, h) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadDetails()
    }

    // Port of Doki's ReadingTimeUseCase, simplified: uses a default 10 s/page (stats integration deferred).
    private fun computeReadingTime(
        manga: Manga,
        history: org.nekosukuriputo.nekuva.core.model.MangaHistory?,
    ): ReadingTimeInfo? {
        if (!settings.prefBoolean(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_READING_TIME, true)) return null
        val chapters = manga.chapters
        if (chapters.isNullOrEmpty()) return null
        val isOnHistoryBranch = history != null && chapters.any { it.id == history.chapterId }
        val secondsPerPage = 10 // default; replaced by stats getTimePerPage once stats is migrated
        var averageTimeSec = 20 /* pages */ * secondsPerPage * chapters.size
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
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            try {
                // 1. Find Manga in DB
                var manga = mangaDataRepository.findMangaById(mangaId, withChapters = true)
                if (manga == null) {
                    _uiState.value = DetailsUiState.Error(IllegalArgumentException("Manga with ID $mangaId not found in local cache."))
                    return@launch
                }
                
                // 2. Fetch details: local manga read their chapters from the file; remote from the parser.
                if (manga!!.isLocal) {
                    manga = localMangaRepository.getDetails(manga!!)
                    // Persist the local chapters (real ids from index.json) so the reader can resolve
                    // a chapter by id via findMangaById(withChapters = true).
                    mangaDataRepository.storeManga(manga!!, replaceExisting = true)
                } else {
                    val source = MangaParserSource.entries.find { it.name == manga!!.source.name }
                    if (source != null) {
                        val parserRepository = repositoryFactory.create(source)
                        manga = parserRepository.getDetails(manga!!)
                        // Save the fetched details and chapters to DB
                        mangaDataRepository.storeManga(manga!!, replaceExisting = true)
                    }
                }

                _uiState.value = DetailsUiState.Success(manga!!)
                loadedManga.value = manga
                loadRelatedManga(manga!!)
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error(e)
            }
        }
    }

    fun retry() {
        loadDetails()
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
                    repositoryFactory.create(source).getPages(chapter)
                }
                _pagesState.value = if (pages.isEmpty()) PagesPreviewState.Empty
                    else PagesPreviewState.Success(chapter.id, pages)
            } catch (e: Exception) {
                _pagesState.value = PagesPreviewState.Error(e)
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
        val chapterId: Long,
        val pages: List<org.nekosukuriputo.nekuva.parsers.model.MangaPage>,
    ) : PagesPreviewState
    data class Error(val error: Throwable) : PagesPreviewState
}
