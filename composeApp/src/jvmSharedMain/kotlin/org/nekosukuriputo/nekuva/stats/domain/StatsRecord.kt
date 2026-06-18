package org.nekosukuriputo.nekuva.stats.domain

import org.nekosukuriputo.nekuva.parsers.model.Manga

/**
 * One row of the reading-stats pie/list (port of Doki StatsRecord): total read [duration] (ms) for a
 * [manga], or the aggregated "Other" bucket when [manga] is null. [hours]/[minutes] are precomputed.
 */
data class StatsRecord(
    val manga: Manga?,
    val duration: Long,
) {
    val hours: Int = (duration / 3_600_000L).toInt()
    val minutes: Int = ((duration / 60_000L) % 60L).toInt()
}

/** Per-manga totals for the manga stats sheet (Doki MangaStatsSheet). */
data class MangaStatsInfo(
    val totalDurationMs: Long,
    val totalPages: Int,
    val startedAt: Long?,
)
