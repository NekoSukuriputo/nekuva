package org.nekosukuriputo.nekuva.scrobbling.mal.data

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
import java.security.SecureRandom
import java.util.Base64

private const val BASE_WEB_URL = "https://myanimelist.net"
private const val BASE_API_URL = "https://api.myanimelist.net/v2"

/** MyAnimeList OAuth2 (PKCE, no client secret) + REST (port of Doki, ScrobblerConfig client id). */
class MALRepository(
    private val okHttp: OkHttpClient,
    private val storage: ScrobblerStorage,
    private val db: MangaDatabase,
) : ScrobblerRepository {

    private val clientId get() = ScrobblerConfig.MAL_CLIENT_ID

    // PKCE: "plain" method → code_challenge == code_verifier. Generated once per repository instance
    // (Koin single), so the value used to build oauthUrl is reused on authorize().
    private val codeVerifier: String by lazy(::generateCodeVerifier)

    override val oauthUrl: String
        get() = "$BASE_WEB_URL/v1/oauth2/authorize?" +
            "response_type=code" +
            "&client_id=$clientId" +
            "&redirect_uri=${ScrobblerConfig.REDIRECT_URI}" +
            "&code_challenge=$codeVerifier" +
            "&code_challenge_method=plain"

    override val isAuthorized: Boolean
        get() = storage.accessToken != null

    override val cachedUser: ScrobblerUser?
        get() = storage.user

    override suspend fun authorize(code: String?) {
        val body = FormBody.Builder()
        if (code != null) {
            body.add("client_id", clientId)
            body.add("grant_type", "authorization_code")
            body.add("code", code)
            body.add("redirect_uri", ScrobblerConfig.REDIRECT_URI)
            body.add("code_verifier", codeVerifier)
        } else {
            body.add("client_id", clientId)
            body.add("grant_type", "refresh_token")
            body.add("refresh_token", checkNotNull(storage.refreshToken))
        }
        val request = Request.Builder().post(body.build()).url("$BASE_WEB_URL/v1/oauth2/token")
        val response = okHttp.newCall(request.build()).await().json()
        storage.accessToken = response.getString("access_token")
        storage.refreshToken = response.getString("refresh_token")
    }

    override suspend fun loadUser(): ScrobblerUser {
        val request = Request.Builder().get().url("$BASE_API_URL/users/@me")
        val response = okHttp.newCall(request.build()).await().json()
        return malUser(response).also { storage.user = it }
    }

    override fun logout() = storage.clear()

    override suspend fun unregister(mangaId: Long) =
        db.getScrobblingDao().delete(ScrobblerService.MAL.id, mangaId)

    override suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> {
        val url = BASE_API_URL.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("nsfw", "true")
            // WARNING! MAL API throws a 400 when the query is over 64 characters.
            .addQueryParameter("q", query.take(64))
            .build()
        val response = okHttp.newCall(Request.Builder().url(url).get().build()).await().json()
        check(response.has("data")) { "Invalid response: \"$response\"" }
        return response.getJSONArray("data").mapJSON { jsonToManga(it, query) }
    }

    override suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo {
        val url = BASE_API_URL.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id.toString())
            .addQueryParameter("fields", "synopsis")
            .build()
        val response = okHttp.newCall(Request.Builder().url(url).get().build()).await().json()
        return ScrobblerMangaInfo(
            id = response.getLong("id"),
            name = response.getString("title"),
            cover = response.optJSONObject("main_picture")?.getStringOrNull("large").orEmpty(),
            url = "$BASE_WEB_URL/manga/${response.getLong("id")}",
            descriptionHtml = response.getStringOrNull("synopsis").orEmpty(),
        )
    }

    override suspend fun createRate(mangaId: Long, scrobblerMangaId: Long) {
        val body = FormBody.Builder().add("status", "reading").add("score", "0")
        val url = BASE_API_URL.toHttpUrl().newBuilder()
            .addPathSegment("manga").addPathSegment(scrobblerMangaId.toString()).addPathSegment("my_list_status")
            .addQueryParameter("fields", "synopsis").build()
        val response = okHttp.newCall(Request.Builder().url(url).put(body.build()).build()).await().json()
        saveRate(response, mangaId, scrobblerMangaId)
    }

    override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
        val body = FormBody.Builder().add("num_chapters_read", chapter.toString())
        val url = BASE_API_URL.toHttpUrl().newBuilder()
            .addPathSegment("manga").addPathSegment(rateId.toString()).addPathSegment("my_list_status").build()
        val response = okHttp.newCall(Request.Builder().url(url).put(body.build()).build()).await().json()
        saveRate(response, mangaId, rateId.toLong())
    }

    override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
        val body = FormBody.Builder()
            .add("status", status.toString())
            .add("score", rating.toInt().toString())
            .add("comments", comment.orEmpty())
        val url = BASE_API_URL.toHttpUrl().newBuilder()
            .addPathSegment("manga").addPathSegment(rateId.toString()).addPathSegment("my_list_status").build()
        val response = okHttp.newCall(Request.Builder().url(url).put(body.build()).build()).await().json()
        saveRate(response, mangaId, rateId.toLong())
    }

    private suspend fun saveRate(json: JSONObject, mangaId: Long, scrobblerMangaId: Long) {
        db.getScrobblingDao().upsert(
            ScrobblingEntity(
                scrobbler = ScrobblerService.MAL.id,
                id = scrobblerMangaId.toInt(),
                mangaId = mangaId,
                targetId = scrobblerMangaId,
                status = json.getString("status"),
                chapter = json.getInt("num_chapters_read"),
                comment = json.getStringOrNull("comments"),
                rating = (json.getDouble("score").toFloat() / 10f).coerceIn(0f, 1f),
            ),
        )
    }

    private fun jsonToManga(json: JSONObject, sourceTitle: String): ScrobblerManga {
        val node = json.getJSONObject("node")
        val title = node.getString("title")
        return ScrobblerManga(
            id = node.getLong("id"),
            name = title,
            altName = null,
            cover = node.optJSONObject("main_picture")?.getStringOrNull("large"),
            url = "$BASE_WEB_URL/manga/${node.getLong("id")}",
            isBestMatch = title.equals(sourceTitle, ignoreCase = true),
        )
    }

    private fun malUser(json: JSONObject) = ScrobblerUser(
        id = json.getLong("id"),
        nickname = json.getString("name"),
        avatar = json.getStringOrNull("picture"),
        service = ScrobblerService.MAL,
    )

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(50)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun Response.json(): JSONObject = use { JSONObject(it.body?.string().orEmpty()) }
}
