package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

class StorageNetworkViewModel(
    private val settings: AppSettings,
    private val cookieJar: MutableCookieJar,
) : ViewModel() {

    private val _imagesProxy = MutableStateFlow(settings.imagesProxy)
    val imagesProxy: StateFlow<Int> = _imagesProxy.asStateFlow()

    val onCookiesCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun setImagesProxy(value: Int) {
        settings.imagesProxy = value
        _imagesProxy.value = value
    }

    fun clearCookies() {
        viewModelScope.launch {
            cookieJar.clear()
            onCookiesCleared.emit(Unit)
        }
    }
}
