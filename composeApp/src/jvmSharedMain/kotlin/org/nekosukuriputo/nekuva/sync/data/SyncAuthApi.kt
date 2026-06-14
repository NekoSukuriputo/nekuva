package org.nekosukuriputo.nekuva.sync.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.nekosukuriputo.nekuva.parsers.util.await
import org.nekosukuriputo.nekuva.sync.domain.SyncApiException

/**
 * Login against the sync server. KMP port of Doki's `SyncAuthApi`: `POST {host}/auth` with
 * `{ email, password }` returns `{ token }`. The same endpoint creates the account if it does not
 * exist yet, so "sign in" and "register" are one call (as in Doki).
 */
class SyncAuthApi(
	private val okHttpClient: OkHttpClient,
) {

	suspend fun authenticate(host: String, email: String, password: String): String {
		val body = json.encodeToString(AuthRequest(email, password)).toRequestBody(MEDIA_TYPE_JSON)
		val request = Request.Builder()
			.url("$host/auth")
			.post(body)
			.build()
		val response = okHttpClient.newCall(request).await()
		val raw = response.body.string()
		if (response.isSuccessful) {
			return json.decodeFromString<AuthResponse>(raw).token
		} else {
			throw SyncApiException(raw.trim().removeSurrounding("\""), response.code)
		}
	}

	@Serializable
	private data class AuthRequest(
		@SerialName("email") val email: String,
		@SerialName("password") val password: String,
	)

	@Serializable
	private data class AuthResponse(
		@SerialName("token") val token: String,
	)

	private companion object {
		val MEDIA_TYPE_JSON = "application/json".toMediaType()
		val json = Json { ignoreUnknownKeys = true }
	}
}
