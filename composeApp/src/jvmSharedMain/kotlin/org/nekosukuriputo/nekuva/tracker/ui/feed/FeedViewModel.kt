package org.nekosukuriputo.nekuva.tracker.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.TrackerDownloadStrategy
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadTask
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.tracker.domain.CheckNewChaptersUseCase
import org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaTracking
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaUpdates

private const val MAX_PARALLELISM = 4

class FeedViewModel(
    private val trackingRepository: TrackingRepository,
    private val checkNewChaptersUseCase: CheckNewChaptersUseCase,
    private val settings: AppSettings,
    private val downloadManager: DownloadManager,
    private val localMangaRepository: LocalMangaRepository,
) : ViewModel() {

    val updatedManga: StateFlow<List<MangaTracking>> =
        trackingRepository.observeUpdatedManga(limit = 200, filterOptions = emptySet())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Keep the tracked set in sync with favourites/history whenever the Feed is opened.
        viewModelScope.launch { runCatchingCancellable { trackingRepository.updateTracks() } }
    }

    /** Check every tracked manga for new chapters (bounded parallelism), saving updates as they finish. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                runCatchingCancellable { trackingRepository.updateTracks() }
                val tracks = runCatchingCancellable { trackingRepository.getTracks() }.getOrNull().orEmpty()
                val semaphore = Semaphore(MAX_PARALLELISM)
                tracks.map { track ->
                    launch {
                        semaphore.withPermit {
                            runCatchingCancellable {
                                val updates = checkNewChaptersUseCase.check(track.manga)
                                trackingRepository.saveUpdates(updates)
                                maybeAutoDownload(updates)
                            }
                        }
                    }
                }.forEach { it.join() }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Auto-download newly-found chapters (Doki tracker_download). DOWNLOADED strategy = only for manga that
     * already have downloaded chapters locally; DISABLED = off. Runs during the Feed check (Nekuva has no
     * background worker, so this is the equivalent trigger).
     */
    private suspend fun maybeAutoDownload(updates: MangaUpdates) {
        if (settings.trackerDownloadStrategy == TrackerDownloadStrategy.DISABLED) return
        if (updates !is MangaUpdates.Success || !updates.isValid || updates.newChapters.isEmpty()) return
        if (updates.manga.id !in localMangaRepository.observeSavedIds().value) return
        runCatchingCancellable {
            downloadManager.schedule(
                listOf(
                    DownloadTask(
                        manga = updates.manga,
                        chaptersIds = updates.newChapters.mapTo(HashSet()) { it.id },
                        destination = null,
                        format = null,
                        startPaused = false,
                    ),
                ),
            )
        }
    }

    fun markRead(mangaId: Long) {
        viewModelScope.launch { runCatchingCancellable { trackingRepository.clearUpdates(listOf(mangaId)) } }
    }

    fun clearAll() {
        viewModelScope.launch { runCatchingCancellable { trackingRepository.clearCounters() } }
    }
}
