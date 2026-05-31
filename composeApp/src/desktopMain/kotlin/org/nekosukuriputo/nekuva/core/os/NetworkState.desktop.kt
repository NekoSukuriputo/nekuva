package org.nekosukuriputo.nekuva.core.os

import coil3.network.ConnectivityChecker
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.util.MediatorStateFlow

actual class NetworkState(
    private val settings: AppSettings
) : MediatorStateFlow<Boolean>(true), ConnectivityChecker {
    actual override val value: Boolean = true
    actual override fun isOnline(): Boolean = true
    actual fun isMetered(): Boolean = false
    actual fun isDataSaverEnabled(): Boolean = false
    actual fun isRestricted(): Boolean = false
    actual fun isOfflineOrRestricted(): Boolean = false
    actual suspend fun awaitForConnection() {}

    override fun onActive() {}
    override fun onInactive() {}
}
