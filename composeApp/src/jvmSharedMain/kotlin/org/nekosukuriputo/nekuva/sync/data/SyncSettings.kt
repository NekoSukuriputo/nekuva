package org.nekosukuriputo.nekuva.sync.data

import com.russhwolf.settings.ObservableSettings

/**
 * Persists the sync account + per-resource enable flags. KMP replacement for Doki's `SyncSettings`,
 * which stored everything in the Android `AccountManager` (host URL, credentials, token, last-sync
 * timestamps). Here it all lives in the shared [ObservableSettings].
 */
class SyncSettings(
	private val prefs: ObservableSettings,
) {

	var host: String
		get() = prefs.getStringOrNull(KEY_HOST)?.withHttpSchema().orEmptyToDefault()
		set(value) = prefs.putString(KEY_HOST, value)

	var email: String?
		get() = prefs.getStringOrNull(KEY_EMAIL)
		set(value) = put(KEY_EMAIL, value)

	/** Stored so a 401 can transparently re-authenticate (mirrors Doki reusing the account password). */
	var password: String?
		get() = prefs.getStringOrNull(KEY_PASSWORD)
		set(value) = put(KEY_PASSWORD, value)

	var token: String?
		get() = prefs.getStringOrNull(KEY_TOKEN)
		set(value) = put(KEY_TOKEN, value)

	var isFavouritesEnabled: Boolean
		get() = prefs.getBoolean(KEY_FAVOURITES, true)
		set(value) = prefs.putBoolean(KEY_FAVOURITES, value)

	var isHistoryEnabled: Boolean
		get() = prefs.getBoolean(KEY_HISTORY, true)
		set(value) = prefs.putBoolean(KEY_HISTORY, value)

	var lastSyncFavourites: Long
		get() = prefs.getLong(KEY_LAST_FAVOURITES, 0L)
		set(value) = prefs.putLong(KEY_LAST_FAVOURITES, value)

	var lastSyncHistory: Long
		get() = prefs.getLong(KEY_LAST_HISTORY, 0L)
		set(value) = prefs.putLong(KEY_LAST_HISTORY, value)

	val isLoggedIn: Boolean
		get() = !token.isNullOrEmpty() && !email.isNullOrEmpty()

	fun logout() {
		prefs.remove(KEY_EMAIL)
		prefs.remove(KEY_PASSWORD)
		prefs.remove(KEY_TOKEN)
		prefs.remove(KEY_LAST_FAVOURITES)
		prefs.remove(KEY_LAST_HISTORY)
	}

	private fun put(key: String, value: String?) {
		if (value == null) prefs.remove(key) else prefs.putString(key, value)
	}

	private fun String?.orEmptyToDefault() = if (isNullOrEmpty()) DEFAULT_HOST else this

	companion object {
		const val KEY_HOST = "sync_host"
		const val KEY_EMAIL = "sync_email"
		const val KEY_PASSWORD = "sync_password"
		const val KEY_TOKEN = "sync_token"
		const val KEY_FAVOURITES = "sync_favourites"
		const val KEY_HISTORY = "sync_history"
		const val KEY_LAST_FAVOURITES = "sync_last_favourites"
		const val KEY_LAST_HISTORY = "sync_last_history"

		// First entry of Doki's sync_url_list — the official Kotatsu-compatible server.
		const val DEFAULT_HOST = "https://sync.kotatsu.app"

		private fun String.withHttpSchema(): String =
			if (startsWith("http://") || startsWith("https://")) this else "https://$this"
	}
}
