package org.nekosukuriputo.nekuva.scrobbling.kitsu.domain

import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus
import org.nekosukuriputo.nekuva.scrobbling.kitsu.data.KitsuRepository

class KitsuScrobbler(
    private val repository: KitsuRepository,
    db: MangaDatabase,
    mangaRepositoryFactory: MangaRepository.Factory,
) : Scrobbler(db, ScrobblerService.KITSU, repository, mangaRepositoryFactory) {

    init {
        statuses[ScrobblingStatus.PLANNED] = "planned"
        statuses[ScrobblingStatus.READING] = "current"
        statuses[ScrobblingStatus.COMPLETED] = "completed"
        statuses[ScrobblingStatus.ON_HOLD] = "on_hold"
        statuses[ScrobblingStatus.DROPPED] = "dropped"
    }

    override suspend fun updateScrobblingInfo(mangaId: Long, rating: Float, status: ScrobblingStatus?, comment: String?) {
        val entity = requireNotNull(db.getScrobblingDao().find(scrobblerService.id, mangaId)) {
            "Scrobbling info for manga $mangaId not found"
        }
        // Kitsu repo scales rating (0..1) by 20 itself, so pass it through unscaled (unlike MAL/Shikimori).
        repository.updateRate(entity.id, entity.mangaId, rating, statuses[status], comment)
    }
}
