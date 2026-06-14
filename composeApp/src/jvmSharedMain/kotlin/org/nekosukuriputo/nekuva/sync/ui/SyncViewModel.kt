package org.nekosukuriputo.nekuva.sync.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nekosukuriputo.nekuva.core.ui.BaseViewModel
import org.nekosukuriputo.nekuva.sync.data.SyncSettings
import org.nekosukuriputo.nekuva.sync.domain.SyncManager
import org.nekosukuriputo.nekuva.sync.domain.SyncState

class SyncViewModel(
	private val manager: SyncManager,
	private val settings: SyncSettings,
) : BaseViewModel() {

	data class UiState(
		val host: String,
		val isLoggedIn: Boolean,
		val email: String?,
		val favouritesEnabled: Boolean,
		val historyEnabled: Boolean,
		val lastSyncFavourites: Long,
		val lastSyncHistory: Long,
	)

	private val _uiState = MutableStateFlow(snapshot())
	val uiState: StateFlow<UiState> = _uiState.asStateFlow()

	val syncState: StateFlow<SyncState> = manager.state

	private fun snapshot() = UiState(
		host = settings.host,
		isLoggedIn = settings.isLoggedIn,
		email = settings.email,
		favouritesEnabled = settings.isFavouritesEnabled,
		historyEnabled = settings.isHistoryEnabled,
		lastSyncFavourites = settings.lastSyncFavourites,
		lastSyncHistory = settings.lastSyncHistory,
	)

	private fun refresh() {
		_uiState.value = snapshot()
	}

	fun setFavouritesEnabled(value: Boolean) {
		settings.isFavouritesEnabled = value
		refresh()
	}

	fun setHistoryEnabled(value: Boolean) {
		settings.isHistoryEnabled = value
		refresh()
	}

	fun login(host: String, email: String, password: String) {
		launchLoadingJob(Dispatchers.Default) {
			manager.login(host, email, password)
			refresh()
			// Pull/push immediately so the user sees their data right after signing in.
			manager.syncNow()
			refresh()
		}
	}

	fun logout() {
		manager.logout()
		refresh()
	}

	fun syncNow() {
		launchLoadingJob(Dispatchers.Default) {
			manager.syncNow()
			refresh()
		}
	}
}
