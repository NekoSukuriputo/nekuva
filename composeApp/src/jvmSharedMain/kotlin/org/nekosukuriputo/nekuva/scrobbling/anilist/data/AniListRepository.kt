package org.nekosukuriputo.nekuva.scrobbling.anilist.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.nekosukuriputo.nekuva.core.db.MangaDatabase
import org.nekosukuriputo.nekuva.parsers.util.await
import org.nekosukuriputo.nekuva.parsers.util.json.getStringOrNull
import org.nekosukuriputo.nekuva.parsers.util.json.mapJSON
import org.nekosukuriputo.nekuva.scrobbling.common.ScrobblerConfig
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerRepository
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblerStorage
import org.nekosukuriputo.nekuva.scrobbling.common.data.ScrobblingEntity
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerManga
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerMangaInfo
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerUser
import java.io.IOException
import kotlin.math.roundToInt

private const val BASE_URL = "https://anilist.co/api/v2/"
private const val ENDPOINT = "https://graphql.anilist.co"
private const val MANGA_PAGE_SIZE = 10
private const val REQUEST_QUERY = "query"
private const val REQUEST_MUTATION = "mutation"
private const val KEY_SCORE_FORMAT = "score_format"

/** AniList OAuth2 + GraphQL (port of Doki, adapted: ScrobblerConfig client id, inline JSON helpers). */
class AniListRepository(
    private val okHttp: OkHttpClient,
    private val storage: ScrobblerStorage,
    private val db: MangaDatabase,
) : ScrobblerRepository {

    private val clientId get() = ScrobblerConfig.ANILIST_CLIENT_ID
    private val clientSecret get() = ScrobblerConfig.ANILIST_CLIENT_SECRET

    private val shrinkRegex = Regex("\\t+")

    override val oauthUrl: String
        get() = "${BASE_URL}oauth/authorize?client_id=$clientId&" +
            "redirect_uri=${ScrobblerConfig.REDIRECT_URI}&response_type=code"

    override val isAuthorized: Boolean
        get() = storage.accessToken != null

    override val cachedUser: ScrobblerUser?
        get() = storage.user

    override suspend fun authorize(code: String?) {
        val body = FormBody.Builder()
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)
        if (code != null) {
            body.add("grant_type", "authorization_code")
            body.add("redirect_uri", ScrobblerConfig.REDIRECT_URI)
            body.add("code", code)
        } else {
            body.add("grant_type", "refresh_token")
            body.add("refresh_token", checkNotNull(storage.refreshToken))
        }
        val request = Request.Builder().post(body.build()).url("${BASE_URL}oauth/token")
        val response = okHttp.newCall(request.build()).await().json()
        storage.accessToken = response.getString("access_token")
        storage.refreshToken = response.getString("refresh_token")
    }

    override suspend fun loadUser(): ScrobblerUser {
        val response = doRequest(
            REQUEST_QUERY,
            """
            AniChartUser {
                user {
                    id
                    name
                    avatar {
                        medium
                    }
                    mediaListOptions {
                        scoreFormat
                    }
                }
            }
        """,
        )
        val jo = response.getJSONObject("data").getJSONObject("AniChartUser").getJSONObject("user")
        storage[KEY_SCORE_FORMAT] = jo.getJSONObject("mediaListOptions").getString("scoreFormat")
        return aniListUser(jo).also { storage.user = it }
    }

    override fun logout() = storage.clear()

    override suspend fun unregister(mangaId: Long) =
        db.getScrobblingDao().delete(ScrobblerService.ANILIST.id, mangaId)

    override suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> {
        val page = offset / MANGA_PAGE_SIZE + 1
        val response = doRequest(
            REQUEST_QUERY,
            """
            Page(page: $page, perPage: $MANGA_PAGE_SIZE) {
                media(type: MANGA, sort: SEARCH_MATCH, search: ${JSONObject.quote(query)}) {
                    id
                    title {
                        userPreferred
                        native
                    }
                    coverImage {
                        medium
                    }
                    siteUrl
                }
            }
        """,
        )
        val data = response.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        return data.mapJSON { parseScrobblerManga(it, query) }
    }

    override suspend fun createRate(mangaId: Long, scrobblerMangaId: Long) {
        val response = doRequest(
            REQUEST_MUTATION,
            """
                SaveMediaListEntry(mediaId: $scrobblerMangaId) {
                    id
                    mediaId
                    status
                    notes
                    score
                    progress
                }
            """,
        )
        saveRate(response.getJSONObject("data").getJSONObject("SaveMediaListEntry"), mangaId)
    }

    override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
        val response = doRequest(
            REQUEST_MUTATION,
            """
                SaveMediaListEntry(id: $rateId, progress: $chapter) {
                    id
                    mediaId
                    status
                    notes
                    score
                    progress
                }
            """,
        )
        saveRate(response.getJSONObject("data").getJSONObject("SaveMediaListEntry"), mangaId)
    }

    override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
        val scoreRaw = (rating * 100f).roundToInt()
        val statusString = status?.let { ", status: $it" }.orEmpty()
        val notesString = comment?.let { ", notes: ${JSONObject.quote(it)}" }.orEmpty()
        val response = doRequest(
            REQUEST_MUTATION,
            """
                SaveMediaListEntry(id: $rateId, scoreRaw: $scoreRaw$statusString$notesString) {
                    id
                    mediaId
                    status
                    notes
                    score
                    progress
                }
            """,
        )
        saveRate(response.getJSONObject("data").getJSONObject("SaveMediaListEntry"), mangaId)
    }

    override suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo {
        val response = doRequest(
            REQUEST_QUERY,
            """
            Media(id: $id) {
                id
                title {
                    userPreferred
                }
                coverImage {
                    large
                }
                description
                siteUrl
            }
            """,
        )
        val media = response.getJSONObject("data").getJSONObject("Media")
        return ScrobblerMangaInfo(
            id = media.getLong("id"),
            name = media.getJSONObject("title").getString("userPreferred"),
            cover = media.getJSONObject("coverImage").getString("large"),
            url = media.getString("siteUrl"),
            descriptionHtml = media.getString("description"),
        )
    }

    private suspend fun saveRate(json: JSONObject, mangaId: Long) {
        val scoreFormat = ScoreFormat.of(storage[KEY_SCORE_FORMAT])
        db.getScrobblingDao().upsert(
            ScrobblingEntity(
                scrobbler = ScrobblerService.ANILIST.id,
                id = json.getInt("id"),
                mangaId = mangaId,
                targetId = json.getLong("mediaId"),
                status = json.getString("status"),
                chapter = json.getInt("progress"),
                comment = json.getString("notes"),
                rating = scoreFormat.normalize(json.getDouble("score").toFloat()),
            ),
        )
    }

    private fun parseScrobblerManga(json: JSONObject, sourceTitle: String): ScrobblerManga {
        val title = json.getJSONObject("title")
        return ScrobblerManga(
            id = json.getLong("id"),
            name = title.getString("userPreferred"),
            altName = title.getStringOrNull("native"),
            cover = json.getJSONObject("coverImage").getString("medium"),
            url = json.getString("siteUrl"),
            isBestMatch = run {
                title.keys().forEach { key ->
                    if (title.getStringOrNull(key)?.equals(sourceTitle, ignoreCase = true) == true) {
                        return@run true
                    }
                }
                false
            },
        )
    }

    private fun aniListUser(json: JSONObject) = ScrobblerUser(
        id = json.getLong("id"),
        nickname = json.getString("name"),
        avatar = json.getJSONObject("avatar").getStringOrNull("medium"),
        service = ScrobblerService.ANILIST,
    )

    private suspend fun doRequest(type: String, payload: String): JSONObject {
        val body = JSONObject()
        body.put("query", "$type { ${payload.shrink()} }")
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)
        val request = Request.Builder().post(requestBody).url(ENDPOINT)
        val json = okHttp.newCall(request.build()).await().json()
        json.optJSONArray("errors")?.let {
            if (it.length() != 0) {
                throw IOException("AniList: $it")
            }
        }
        return json
    }

    private fun String.shrink() = replace(shrinkRegex, " ")

    private fun Response.json(): JSONObject = use { JSONObject(it.body?.string().orEmpty()) }
}
