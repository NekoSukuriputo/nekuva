package org.nekosukuriputo.nekuva.download.domain

import kotlinx.serialization.Serializable
import java.io.File

/** File where the unfinished download queue is persisted (Android: filesDir; Desktop: ~/.nekuva). */
expect fun downloadQueueFile(): File

/**
 * A persisted download task (Doki: WorkManager survives app-kill; the in-process engine persists the
 * queue itself). Only lightweight fields are stored — the [Manga] is reconstructed from the Room `manga`
 * table by [mangaId] on restore (it was upserted when the download was scheduled).
 */
@Serializable
data class PersistedDownload(
    val mangaId: Long,
    val chaptersIds: List<Long>? = null,
    val destinationPath: String? = null,
    val format: String? = null,
)
