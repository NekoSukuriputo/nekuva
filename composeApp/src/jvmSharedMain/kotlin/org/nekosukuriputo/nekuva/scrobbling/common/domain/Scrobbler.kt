package org.nekosukuriputo.nekuva.scrobbling.common.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.core.parser.MangaRepository
import org.nekosukuriputo.nekuva.core.util.ext.printStackTraceDebug
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource
import org.nekosukuriputo.nekuva.parsers.util.findById
import org.nekosukuriputo.nekuva.parsers.util.runCatchingCancellable
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerRepository
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblingEntity
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerMangaInfo
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerManga
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerUser
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingInfo
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus

/**
 * Base for an external tracking service (port of Doki's Scrobbler, adapted for KMP/Compose — no
 * Android `parseAsHtml`/`LongSparseArray`/`EnumMap`). Bridges the per-service [ScrobblerRepository]
 * (OAuth + API) with the local `scrobblings` table.
 */
abstract class Scrobbler(
    protected val db: MangaDatabase,
    val scrobblerService: ScrobblerService,
    private val repository: ScrobblerRepository,
    private val mangaRepositoryFactory: MangaRepository.Factory,
) {

    private val infoCache = HashMap<Long, ScrobblerMangaInfo>()
    protected val statuses = HashMap<ScrobblingStatus, String>()

    val oauthUrl: String get() = repository.oauthUrl

    val user: Flow<ScrobblerUser> = flow {
        repository.cachedUser?.let { emit(it) }
        runCatchingCancellable { repository.loadUser() }
            .onSuccess { emit(it) }
            .onFailure { it.printStackTraceDebug() }
    }

    val isEnabled: Boolean
        get() = repository.isAuthorized

    suspend fun authorize(authCode: String): ScrobblerUser {
        repository.authorize(authCode)
        return repository.loadUser()
    }

    fun logout() = repository.logout()

    suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> = repository.findManga(query, offset)

    suspend fun linkManga(mangaId: Long, targetId: Long) = repository.createRate(mangaId, targetId)

    suspend fun scrobble(manga: Manga, chapterId: Long) {
        var chapters = manga.chapters
        if (chapters.isNullOrEmpty()) {
            val source = MangaParserSource.entries.find { it.name == manga.source.name } ?: return
            chapters = mangaRepositoryFactory.create(source).getDetails(manga).chapters
        }
        requireNotNull(chapters)
        val chapter = checkNotNull(chapters.findById(chapterId)) { "Chapter $chapterId not found in this manga" }
        val number = if (chapter.number > 0f) {
            chapter.number.toInt()
        } else {
            val branchChapters = chapters.filter { it.branch == chapter.branch }
            branchChapters.indexOf(chapter) + 1
        }
        val entity = db.getScrobblingDao().find(scrobblerService.id, manga.id) ?: return
        repository.updateRate(entity.id, entity.mangaId, number)
    }

    suspend fun getScrobblingInfoOrNull(mangaId: Long): ScrobblingInfo? {
        val entity = db.getScrobblingDao().find(scrobblerService.id, mangaId) ?: return null
        return entity.toScrobblingInfo()
    }

    abstract suspend fun updateScrobblingInfo(
        mangaId: Long,
        rating: Float,
        status: ScrobblingStatus?,
        comment: String?,
    )

    fun observeScrobblingInfo(mangaId: Long): Flow<ScrobblingInfo?> =
        db.getScrobblingDao().observe(scrobblerService.id, mangaId).map { it?.toScrobblingInfo() }

    fun observeAllScrobblingInfo(): Flow<List<ScrobblingInfo>> =
        db.getScrobblingDao().observe(scrobblerService.id).map { entities ->
            entities.mapNotNull { it.toScrobblingInfo() }
        }

    suspend fun unregisterScrobbling(mangaId: Long) = repository.unregister(mangaId)

    protected suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo = repository.getMangaInfo(id)

    private suspend fun ScrobblingEntity.toScrobblingInfo(): ScrobblingInfo? {
        val mangaInfo = infoCache[targetId] ?: runCatchingCancellable { getMangaInfo(targetId) }
            .onFailure { it.printStackTraceDebug() }
            .onSuccess { infoCache[targetId] = it }
            .getOrNull() ?: return null
        return ScrobblingInfo(
            scrobbler = scrobblerService,
            mangaId = mangaId,
            targetId = targetId,
            status = statuses.entries.find { it.value == status }?.key,
            chapter = chapter,
            comment = comment,
            rating = rating,
            title = mangaInfo.name,
            coverUrl = mangaInfo.cover,
            description = stripHtml(mangaInfo.descriptionHtml),
            externalUrl = mangaInfo.url,
        )
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
}

suspend fun Scrobbler.tryScrobble(manga: Manga, chapterId: Long): Boolean =
    runCatchingCancellable { scrobble(manga, chapterId) }
        .onFailure { it.printStackTraceDebug() }
        .isSuccess
