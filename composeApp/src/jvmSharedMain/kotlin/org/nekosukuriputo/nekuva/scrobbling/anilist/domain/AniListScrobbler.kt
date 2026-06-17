package org.nekosukuriputo.nekuva.scrobbling.anilist.domain

import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.scrobbling.anilist.data.AniListRepository
import org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus

class AniListScrobbler(
    private val repository: AniListRepository,
    db: MangaDatabase,
    mangaRepositoryFactory: MangaRepository.Factory,
) : Scrobbler(db, ScrobblerService.ANILIST, repository, mangaRepositoryFactory) {

    init {
        statuses[ScrobblingStatus.PLANNED] = "PLANNING"
        statuses[ScrobblingStatus.READING] = "CURRENT"
        statuses[ScrobblingStatus.RE_READING] = "REPEATING"
        statuses[ScrobblingStatus.COMPLETED] = "COMPLETED"
        statuses[ScrobblingStatus.ON_HOLD] = "PAUSED"
        statuses[ScrobblingStatus.DROPPED] = "DROPPED"
    }

    override suspend fun updateScrobblingInfo(mangaId: Long, rating: Float, status: ScrobblingStatus?, comment: String?) {
        val entity = requireNotNull(db.getScrobblingDao().find(scrobblerService.id, mangaId)) {
            "Scrobbling info for manga $mangaId not found"
        }
        repository.updateRate(entity.id, entity.mangaId, rating, statuses[status], comment)
    }
}
