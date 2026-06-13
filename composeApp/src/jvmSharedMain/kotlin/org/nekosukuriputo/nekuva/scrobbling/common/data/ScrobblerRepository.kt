package org.nekosukuriputo.nekuva.scrobbling.common.data

import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerManga
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerMangaInfo
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerUser

/** Per-service OAuth + API surface backing a [org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler]. */
interface ScrobblerRepository {

    val oauthUrl: String

    val isAuthorized: Boolean

    val cachedUser: ScrobblerUser?

    suspend fun authorize(code: String?)

    suspend fun loadUser(): ScrobblerUser

    fun logout()

    suspend fun unregister(mangaId: Long)

    suspend fun findManga(query: String, offset: Int): List<ScrobblerManga>

    suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo

    suspend fun createRate(mangaId: Long, scrobblerMangaId: Long)

    suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int)

    suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?)
}
