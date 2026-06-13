package org.nekosukuriputo.nekuva.scrobbling.shikimori.data

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
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

private const val DOMAIN = "shikimori.one"
private const val BASE_URL = "https://$DOMAIN/"
private const val MANGA_PAGE_SIZE = 10

/** Shikimori OAuth + API (port of Doki, adapted: ScrobblerConfig client id, inline JSON helpers). */
class ShikimoriRepository(
    private val okHttp: OkHttpClient,
    private val storage: ScrobblerStorage,
    private val db: MangaDatabase,
) : ScrobblerRepository {

    private val clientId get() = ScrobblerConfig.SHIKIMORI_CLIENT_ID
    private val clientSecret get() = ScrobblerConfig.SHIKIMORI_CLIENT_SECRET

    override val oauthUrl: String
        get() = "${BASE_URL}oauth/authorize?client_id=$clientId&" +
            "redirect_uri=${ScrobblerConfig.REDIRECT_URI}&response_type=code&scope="

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
        val request = Request.Builder().get().url("${BASE_URL}api/users/whoami")
        val response = okHttp.newCall(request.build()).await().json()
        return shikimoriUser(response).also { storage.user = it }
    }

    override fun logout() = storage.clear()

    override suspend fun unregister(mangaId: Long) =
        db.getScrobblingDao().delete(ScrobblerService.SHIKIMORI.id, mangaId)

    override suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> {
        val page = offset / MANGA_PAGE_SIZE
        val pageOffset = offset % MANGA_PAGE_SIZE
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api").addPathSegment("mangas")
            .addEncodedQueryParameter("page", (page + 1).toString())
            .addEncodedQueryParameter("limit", MANGA_PAGE_SIZE.toString())
            .addEncodedQueryParameter("censored", false.toString())
            .addQueryParameter("search", query)
            .build()
        val response = okHttp.newCall(Request.Builder().url(url).get().build()).await().jsonArray()
        val list = response.mapJSON { scrobblerManga(it, query) }
        return if (pageOffset != 0) list.drop(pageOffset) else list
    }

    override suspend fun createRate(mangaId: Long, scrobblerMangaId: Long) {
        val user = cachedUser ?: loadUser()
        val payload = JSONObject().put(
            "user_rate",
            JSONObject().put("target_id", scrobblerMangaId).put("target_type", "Manga").put("user_id", user.id),
        )
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api").addPathSegment("v2").addPathSegment("user_rates").build()
        val response = okHttp.newCall(Request.Builder().url(url).post(payload.body()).build()).await().json()
        saveRate(response, mangaId)
    }

    override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
        val payload = JSONObject().put("user_rate", JSONObject().put("chapters", chapter))
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api").addPathSegment("v2").addPathSegment("user_rates").addPathSegment(rateId.toString()).build()
        val response = okHttp.newCall(Request.Builder().url(url).patch(payload.body()).build()).await().json()
        saveRate(response, mangaId)
    }

    override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
        val payload = JSONObject().put(
            "user_rate",
            JSONObject().apply {
                put("score", rating.toString())
                if (comment != null) put("text", comment)
                if (status != null) put("status", status)
            },
        )
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api").addPathSegment("v2").addPathSegment("user_rates").addPathSegment(rateId.toString()).build()
        val response = okHttp.newCall(Request.Builder().url(url).patch(payload.body()).build()).await().json()
        saveRate(response, mangaId)
    }

    override suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo {
        val request = Request.Builder().get().url("${BASE_URL}api/mangas/$id")
        val response = okHttp.newCall(request.build()).await().json()
        return ScrobblerMangaInfo(
            id = response.getLong("id"),
            name = response.getString("name"),
            cover = response.getJSONObject("image").getString("preview").abs(),
            url = response.getString("url").abs(),
            descriptionHtml = response.getStringOrNull("description_html").orEmpty(),
        )
    }

    private suspend fun saveRate(json: JSONObject, mangaId: Long) {
        db.getScrobblingDao().upsert(
            ScrobblingEntity(
                scrobbler = ScrobblerService.SHIKIMORI.id,
                id = json.getInt("id"),
                mangaId = mangaId,
                targetId = json.getLong("target_id"),
                status = json.getString("status"),
                chapter = json.getInt("chapters"),
                comment = json.getStringOrNull("text"),
                rating = (json.getDouble("score").toFloat() / 10f).coerceIn(0f, 1f),
            ),
        )
    }

    private fun scrobblerManga(json: JSONObject, sourceTitle: String) = ScrobblerManga(
        id = json.getLong("id"),
        name = json.getString("name"),
        altName = json.getStringOrNull("russian"),
        cover = json.getJSONObject("image").getString("preview").abs(),
        url = json.getString("url").abs(),
        isBestMatch = sourceTitle.equals(json.getString("name"), ignoreCase = true) ||
            json.getStringOrNull("russian")?.equals(sourceTitle, ignoreCase = true) == true,
    )

    private fun shikimoriUser(json: JSONObject) = ScrobblerUser(
        id = json.getLong("id"),
        nickname = json.getString("nickname"),
        avatar = json.getStringOrNull("avatar"),
        service = ScrobblerService.SHIKIMORI,
    )

    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun String.abs(): String =
        if (startsWith("http")) this else "https://$DOMAIN" + (if (startsWith("/")) this else "/$this")

    private fun Response.json(): JSONObject = use { JSONObject(it.body?.string().orEmpty()) }
    private fun Response.jsonArray(): JSONArray = use { JSONArray(it.body?.string().orEmpty()) }
}
