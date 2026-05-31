package org.nekosukuriputo.nekuva.core.os

import coil3.network.ConnectivityChecker
import org.nekosukuriputo.nekuva.core.util.MediatorStateFlow

expect class NetworkState : MediatorStateFlow<Boolean>, ConnectivityChecker {
	override val value: Boolean
	override fun isOnline(): Boolean
	fun isMetered(): Boolean
	fun isDataSaverEnabled(): Boolean
	fun isRestricted(): Boolean
	fun isOfflineOrRestricted(): Boolean
	suspend fun awaitForConnection()
}
