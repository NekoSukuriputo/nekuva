package org.nekosukuriputo.nekuva.download.domain

import org.nekosukuriputo.nekuva.local.domain.model.LocalManga
import org.nekosukuriputo.nekuva.parsers.model.Manga

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class ChapterDownloadStatus { PENDING, DOWNLOADING, DONE, FAILED }

/** Per-chapter progress for the expandable chapter list (mirrors Doki). */
data class DownloadChapterState(
    val id: Long,
    val name: String,
    val status: ChapterDownloadStatus,
    val error: String? = null,
)

/**
 * Snapshot of a single download, emitted by [DownloadManager]. KMP-friendly replacement for Doki's
 * WorkManager-backed `DownloadState` (no `androidx.work.Data`).
 */
data class DownloadState(
    val id: String,
    val manga: Manga,
    val status: DownloadStatus,
    val isIndeterminate: Boolean = true,
    val errorMessage: String? = null,
    val totalChapters: Int = 0,
    val currentChapter: Int = 0,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val downloadedChapters: Int = 0,
    val eta: Long = -1L,
    val localManga: LocalManga? = null,
    val chapters: List<DownloadChapterState> = emptyList(),
    val timestamp: Long = 0L,
) {

    val max: Int = totalChapters * totalPages

    val progress: Int = totalPages * currentChapter + currentPage + 1

    val percent: Float = if (max > 0) progress.toFloat() / max else 0f

    val canPause: Boolean get() = status == DownloadStatus.RUNNING
    val canResume: Boolean get() = status == DownloadStatus.PAUSED
    val canCancel: Boolean get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.RUNNING ||
        status == DownloadStatus.PAUSED
    val isFinished: Boolean get() = status == DownloadStatus.COMPLETED ||
        status == DownloadStatus.FAILED ||
        status == DownloadStatus.CANCELLED
}
