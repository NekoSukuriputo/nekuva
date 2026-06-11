package org.nekosukuriputo.nekuva.download.domain

import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.parsers.model.Manga
import java.io.File

/** A scheduled download request. [chaptersIds] == null means "the whole manga". */
data class DownloadTask(
    val manga: Manga,
    val chaptersIds: Set<Long>?,
    val destination: File?,
    val format: DownloadFormat?,
    val startPaused: Boolean,
)
