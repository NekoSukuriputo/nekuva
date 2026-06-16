package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.local.data.CacheDir
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager

class StorageNetworkViewModel(
    private val settings: AppSettings,
    private val cookieJar: MutableCookieJar,
    private val storageManager: LocalStorageManager,
) : ViewModel() {

    private val _imagesProxy = MutableStateFlow(settings.imagesProxy)
    val imagesProxy: StateFlow<Int> = _imagesProxy.asStateFlow()

    val onCookiesCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Storage breakdown for the meter (Doki StorageAndNetworkSettingsViewModel.loadStorageUsage).
    val storageUsage: StateFlow<StorageUsage?> = flow {
        emit(loadStorageUsage())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    private suspend fun loadStorageUsage(): StorageUsage {
        val pagesCache = storageManager.computeCacheSize(CacheDir.PAGES)
        val otherCache = (storageManager.computeCacheSize() - pagesCache).coerceAtLeast(0L)
        val savedManga = storageManager.computeStorageSize()
        val available = storageManager.computeAvailableSize()
        val total = (pagesCache + otherCache + savedManga + available).coerceAtLeast(1L).toDouble()
        return StorageUsage(
            savedManga = StorageUsage.Item(savedManga, (savedManga / total).toFloat()),
            pagesCache = StorageUsage.Item(pagesCache, (pagesCache / total).toFloat()),
            otherCache = StorageUsage.Item(otherCache, (otherCache / total).toFloat()),
            available = StorageUsage.Item(available, (available / total).toFloat()),
        )
    }

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
