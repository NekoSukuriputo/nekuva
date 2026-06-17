package org.nekosukuriputo.nekuva.scrobbling.mal.domain

import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus
import org.nekosukuriputo.nekuva.scrobbling.mal.data.MALRepository

private const val RATING_MAX = 10f

class MALScrobbler(
    private val repository: MALRepository,
    db: MangaDatabase,
    mangaRepositoryFactory: MangaRepository.Factory,
) : Scrobbler(db, ScrobblerService.MAL, repository, mangaRepositoryFactory) {

    init {
        statuses[ScrobblingStatus.PLANNED] = "plan_to_read"
        statuses[ScrobblingStatus.READING] = "reading"
        statuses[ScrobblingStatus.COMPLETED] = "completed"
        statuses[ScrobblingStatus.ON_HOLD] = "on_hold"
        statuses[ScrobblingStatus.DROPPED] = "dropped"
    }

    override suspend fun updateScrobblingInfo(mangaId: Long, rating: Float, status: ScrobblingStatus?, comment: String?) {
        val entity = requireNotNull(db.getScrobblingDao().find(scrobblerService.id, mangaId)) {
            "Scrobbling info for manga $mangaId not found"
        }
        repository.updateRate(entity.id, entity.mangaId, rating * RATING_MAX, statuses[status], comment)
    }
}
