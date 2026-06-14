package org.nekosukuriputo.nekuva.sync.data

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Refreshes an expired token on a 401, reusing the stored credentials. KMP port of Doki's
 * `SyncAuthenticator`. OkHttp calls this off the main thread, so blocking on the suspend auth call
 * is acceptable (same approach Doki used).
 */
class SyncAuthenticator(
	private val settings: SyncSettings,
	private val authApi: SyncAuthApi,
) : Authenticator {

	override fun authenticate(route: Route?, response: Response): Request? {
		if (responseCount(response) >= 2) {
			return null // already retried once — give up to avoid a loop
		}
		val email = settings.email ?: return null
		val password = settings.password ?: return null
		val newToken = runCatching {
			runBlocking { authApi.authenticate(settings.host, email, password) }
		}.getOrNull() ?: return null
		settings.token = newToken
		return response.request.newBuilder()
			.header("Authorization", "Bearer $newToken")
			.build()
	}

	private fun responseCount(response: Response): Int {
		var result = 1
		var prior = response.priorResponse
		while (prior != null) {
			result++
			prior = prior.priorResponse
		}
		return result
	}
}
