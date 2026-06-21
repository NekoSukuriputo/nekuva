package org.nekosukuriputo.nekuva.sync.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nekosukuriputo.nekuva.sync.data.SyncAuthApi
import org.nekosukuriputo.nekuva.sync.data.SyncSettings
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Coordinates login + on-demand sync. KMP replacement for Doki's `SyncController` (which drove the
 * Android SyncAdapter via `ContentResolver.requestSync` and observed the Room InvalidationTracker).
 *
 * For Phase 1 this exposes a manual [syncNow] (plus a sync right after login). Automatic
 * change-triggered / periodic background sync (Doki's SyncAdapter periodic schedule) is deferred to
 * the background-jobs area — see MIGRATION.md.
 */
@OptIn(ExperimentalTime::class)
class SyncManager(
	private val settings: SyncSettings,
	private val helper: SyncHelper,
	private val authApi: SyncAuthApi,
) {

	private val mutex = Mutex()
	private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
	val state: StateFlow<SyncState> = _state.asStateFlow()

	val isLoggedIn: Boolean get() = settings.isLoggedIn

	/** Sign in (or register — the server creates the account if absent) and persist the credentials. */
	suspend fun login(host: String, email: String, password: String) {
		settings.host = host.trim()
		val token = authApi.authenticate(settings.host, email.trim(), password)
		settings.email = email.trim()
		settings.password = password
		settings.token = token
		// Start periodic background sync now that we're logged in (Android only; Desktop no-op).
		runCatching { org.nekosukuriputo.nekuva.sync.work.scheduleSync() }
	}

	fun logout() {
		settings.logout()
		_state.value = SyncState.Idle
		// Cancel the periodic background sync.
		runCatching { org.nekosukuriputo.nekuva.sync.work.scheduleSync() }
	}

	suspend fun syncNow() = mutex.withLock {
		if (!settings.isLoggedIn) throw SyncApiException("Not logged in", 401)
		_state.value = SyncState.Running
		try {
			if (settings.isFavouritesEnabled) helper.syncFavourites()
			if (settings.isHistoryEnabled) helper.syncHistory()
			_state.value = SyncState.Success(Clock.System.now().toEpochMilliseconds())
		} catch (e: CancellationException) {
			_state.value = SyncState.Idle
			throw e
		} catch (e: Throwable) {
			_state.value = SyncState.Error(e.message ?: e.toString())
			throw e
		}
	}
}

sealed interface SyncState {
	data object Idle : SyncState
	data object Running : SyncState
	data class Success(val timestamp: Long) : SyncState
	data class Error(val message: String) : SyncState
}
