package org.nekosukuriputo.nekuva.stats.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.db.entity.toManga
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.stats.domain.MangaStatsInfo
import org.nekosukuriputo.nekuva.stats.domain.StatsPeriod
import org.nekosukuriputo.nekuva.stats.domain.StatsRecord

/** Reading statistics queries (port of Doki StatsRepository). Feeds the Stats screen + reading-time estimate. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsRepository(
    private val settings: AppSettings,
    private val db: MangaDatabase,
) {

    /** Per-manga total reading time over [period], small contributors (<5%) folded into an "Other" bucket. */
    suspend fun getReadingStats(period: StatsPeriod, categories: Set<Long>): List<StatsRecord> {
        val fromDate = if (period == StatsPeriod.ALL) {
            0L
        } else {
            System.currentTimeMillis() - period.days.toLong() * 24L * 60L * 60L * 1000L
        }
        val stats = db.getStatsDao().getDurationStats(fromDate, null, categories)
        val result = ArrayList<StatsRecord>(stats.size)
        var other = StatsRecord(null, 0)
        val total = stats.values.sum()
        if (total <= 0L) return emptyList()
        for ((mangaEntity, duration) in stats) {
            val percent = duration.toDouble() / total
            if (percent < 0.05) {
                other = other.copy(duration = other.duration + duration)
            } else {
                result += StatsRecord(manga = mangaEntity.toManga(emptySet(), null), duration = duration)
            }
        }
        if (other.duration != 0L) result += other
        return result
    }

    /** Average ms per page for [mangaId] (falls back to the global average when too few samples). */
    suspend fun getTimePerPage(mangaId: Long): Long {
        val dao = db.getStatsDao()
        val pages = dao.getReadPagesCount(mangaId)
        return if (pages >= 10) dao.getAverageTimePerPage(mangaId) else dao.getAverageTimePerPage()
    }

    suspend fun getTotalPagesRead(mangaId: Long): Int = db.getStatsDao().getReadPagesCount(mangaId)

    /** Per-manga totals for the manga stats sheet (Doki MangaStatsSheet): total read time (ms) + pages. */
    suspend fun getMangaStats(mangaId: Long): MangaStatsInfo {
        val sessions = db.getStatsDao().findAll(mangaId)
        return MangaStatsInfo(
            totalDurationMs = sessions.sumOf { it.duration },
            totalPages = db.getStatsDao().getReadPagesCount(mangaId),
            startedAt = sessions.minOfOrNull { it.startedAt },
        )
    }

    suspend fun clearStats() = db.getStatsDao().clear()

    fun observeHasStats(mangaId: Long): Flow<Boolean> =
        settings.observeBoolean(AppSettings.KEY_STATS_ENABLED, false)
            .flatMapLatest { isEnabled ->
                if (isEnabled) db.getStatsDao().observeRowCount(mangaId).map { it > 0 } else flowOf(false)
            }
            .distinctUntilChanged()
}
