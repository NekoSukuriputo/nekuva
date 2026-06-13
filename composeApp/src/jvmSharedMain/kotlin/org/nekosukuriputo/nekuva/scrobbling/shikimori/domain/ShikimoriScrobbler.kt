package org.nekosukuriputo.nekuva.scrobbling.shikimori.domain

import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus
import org.nekosukuriputo.nekuva.scrobbling.shikimori.data.ShikimoriRepository

private const val RATING_MAX = 10f

class ShikimoriScrobbler(
    private val repository: ShikimoriRepository,
    db: MangaDatabase,
    mangaRepositoryFactory: MangaRepository.Factory,
) : Scrobbler(db, ScrobblerService.SHIKIMORI, repository, mangaRepositoryFactory) {

    init {
        statuses[ScrobblingStatus.PLANNED] = "planned"
        statuses[ScrobblingStatus.READING] = "watching"
        statuses[ScrobblingStatus.RE_READING] = "rewatching"
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
