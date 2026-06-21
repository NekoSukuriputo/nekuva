package org.nekosukuriputo.nekuva.tracker.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.TrackerDownloadStrategy
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadTask
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaUpdates

private const val MAX_PARALLELISM = 4

/**
 * Runs the tracker check "now" (Doki TrackWorker.Scheduler.startNow): syncs the tracked set, then checks
 * every tracked manga for new chapters with bounded parallelism, saving updates + auto-downloading per the
 * tracker_download setting. A singleton so the Feed screen AND the shell's "Update" overflow share the same
 * [isRunning] state and never run two checks at once (Doki's single-worker semantics).
 */
class TrackerUpdateUseCase(
    private val trackingRepository: TrackingRepository,
    private val checkNewChaptersUseCase: CheckNewChaptersUseCase,
    private val settings: AppSettings,
    private val downloadManager: DownloadManager,
    private val localMangaRepository: LocalMangaRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Start a check now; a no-op if one is already running (Doki: the worker is unique). */
    fun updateNow() {
        if (!_isRunning.compareAndSet(expect = false, update = true)) return
        scope.launch {
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
                }.joinAll()
            } finally {
                _isRunning.value = false
            }
        }
    }

    /**
     * Auto-download newly-found chapters (Doki tracker_download). DOWNLOADED strategy = only for manga that
     * already have downloaded chapters locally; DISABLED = off.
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
}
