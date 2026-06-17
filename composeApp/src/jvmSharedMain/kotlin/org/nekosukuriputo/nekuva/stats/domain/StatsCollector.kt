package org.nekosukuriputo.nekuva.stats.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.stats.data.StatsEntity

/**
 * Accumulates per-manga reading sessions into the `stats` table (port of Doki StatsCollector). The reader
 * calls [onStateChanged] as the page/chapter changes and [onPause] when it stops; each session row is keyed
 * by its start time, with duration = now - start and pages incremented on each distinct page/chapter.
 */
class StatsCollector(
    private val db: MangaDatabase,
    private val settings: AppSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = HashMap<Long, Entry>()

    @Synchronized
    fun onStateChanged(mangaId: Long, chapterId: Long, page: Int) {
        if (!settings.isStatsEnabled) return
        val now = System.currentTimeMillis()
        val entry = sessions[mangaId]
        if (entry == null) {
            sessions[mangaId] = Entry(chapterId, page, StatsEntity(mangaId, now, 0L, 0))
            return
        }
        val pagesDelta = if (entry.page != page || entry.chapterId != chapterId) 1 else 0
        val newStats = StatsEntity(
            mangaId = mangaId,
            startedAt = entry.stats.startedAt,
            duration = now - entry.stats.startedAt,
            pages = entry.stats.pages + pagesDelta,
        )
        sessions[mangaId] = Entry(chapterId, page, newStats)
        commit(newStats)
    }

    @Synchronized
    fun onPause(mangaId: Long) {
        sessions.remove(mangaId)
    }

    private fun commit(entity: StatsEntity) {
        scope.launch { runCatchingCancellable { db.getStatsDao().upsert(entity) } }
    }

    private data class Entry(val chapterId: Long, val page: Int, val stats: StatsEntity)
}
