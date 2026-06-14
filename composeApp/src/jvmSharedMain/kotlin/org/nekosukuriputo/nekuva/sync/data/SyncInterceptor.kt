package org.nekosukuriputo.nekuva.sync.data

import okhttp3.Interceptor
import okhttp3.Response
import org.nekosukuriputo.nekuva.core.db.DATABASE_VERSION

/**
 * Attaches the bearer token + protocol version headers to every sync request. KMP port of Doki's
 * `SyncInterceptor` (which read the token from the Android AccountManager); here the token comes
 * from [SyncSettings].
 *
 * NOTE: Nekuva's Room schema is "Fresh V1" (version 1), while the canonical Kotatsu schema is much
 * higher. The wire JSON shapes still match, but `X-Db-Version` advertises Nekuva's local version —
 * a self-hosted server is fine; the official server may key behaviour off this. Tracked in MIGRATION.md.
 */
class SyncInterceptor(
	private val settings: SyncSettings,
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val builder = chain.request().newBuilder()
		settings.token?.let { token ->
			builder.header("Authorization", "Bearer $token")
		}
		builder.header("X-App-Version", SYNC_APP_VERSION.toString())
		builder.header("X-Db-Version", DATABASE_VERSION.toString())
		return chain.proceed(builder.build())
	}

	private companion object {
		const val SYNC_APP_VERSION = 1
	}
}
