package org.nekosukuriputo.nekuva.download.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadState
import org.nekosukuriputo.nekuva.download.domain.DownloadStatus

sealed interface DownloadListEntry {
    data object QueuedHeader : DownloadListEntry
    data object InProgressHeader : DownloadListEntry
    data class DateHeader(val timestamp: Long) : DownloadListEntry
    data class DownloadRow(val state: DownloadState) : DownloadListEntry
}

/** Combined capability of the current selection (Doki onPrepareActionMode gates which actions are visible). */
data class DownloadSelectionCapability(
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canCancel: Boolean = false,
    val canRemove: Boolean = false,
)

class DownloadsViewModel(
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val entries: StateFlow<List<DownloadListEntry>> = downloadManager.downloads
        .map { it.toEntries() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasActiveWorks: StateFlow<Boolean> = downloadManager.downloads
        .map { list -> list.any { it.canPause } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasPausedWorks: StateFlow<Boolean> = downloadManager.downloads
        .map { list -> list.any { it.canResume } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasCancellableWorks: StateFlow<Boolean> = downloadManager.downloads
        .map { list -> list.any { it.canCancel } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun pause(id: String) = downloadManager.pause(id)
    fun resume(id: String) = downloadManager.resume(id)
    fun cancel(id: String) = downloadManager.cancel(id)
    fun retry(id: String) = downloadManager.retry(id)
    fun retryChapter(id: String, chapterId: Long) = downloadManager.retryChapter(id, chapterId)
    fun remove(id: String) = downloadManager.remove(id)
    fun pauseAll() = downloadManager.pauseAll()
    fun resumeAll() = downloadManager.resumeAll()
    fun cancelAll() = downloadManager.cancelAll()
    fun removeCompleted() = downloadManager.removeCompleted()

    // --- Multi-select (Doki mode_downloads): apply an action to several downloads at once. ---
    fun pause(ids: Collection<String>) = ids.forEach { downloadManager.pause(it) }
    fun resume(ids: Collection<String>) = ids.forEach { downloadManager.resume(it) }
    fun cancel(ids: Collection<String>) = ids.forEach { downloadManager.cancel(it) }
    fun remove(ids: Collection<String>) = ids.forEach { downloadManager.remove(it) }

    fun allIds(): List<String> = downloadManager.downloads.value.map { it.id }

    /** Combined capability of the selected items (Doki onPrepareActionMode): an action shows only if it applies to all. */
    fun selectionCapability(ids: Set<String>): DownloadSelectionCapability {
        val snapshot = downloadManager.downloads.value.filter { it.id in ids }
        if (snapshot.isEmpty()) return DownloadSelectionCapability()
        return DownloadSelectionCapability(
            canPause = snapshot.all { it.canPause },
            canResume = snapshot.all { it.canResume },
            canCancel = snapshot.all { !it.isFinished },
            canRemove = snapshot.all { it.isFinished },
        )
    }

    private fun List<DownloadState>.toEntries(): List<DownloadListEntry> {
        if (isEmpty()) return emptyList()
        val sorted = sortedByDescending { it.timestamp }
        val queued = sorted.filter { it.status == DownloadStatus.QUEUED }
        val active = sorted.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PAUSED }
        val finished = sorted.filter { it.isFinished }
        val result = ArrayList<DownloadListEntry>(size + 4)
        if (queued.isNotEmpty()) {
            result += DownloadListEntry.QueuedHeader
            queued.forEach { result += DownloadListEntry.DownloadRow(it) }
        }
        if (active.isNotEmpty()) {
            result += DownloadListEntry.InProgressHeader
            active.forEach { result += DownloadListEntry.DownloadRow(it) }
        }
        val dayMillis = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        var prevDay = Long.MIN_VALUE
        for (item in finished) {
            val daysAgo = (now - item.timestamp) / dayMillis
            if (daysAgo != prevDay) {
                result += DownloadListEntry.DateHeader(item.timestamp)
                prevDay = daysAgo
            }
            result += DownloadListEntry.DownloadRow(item)
        }
        return result
    }
}
