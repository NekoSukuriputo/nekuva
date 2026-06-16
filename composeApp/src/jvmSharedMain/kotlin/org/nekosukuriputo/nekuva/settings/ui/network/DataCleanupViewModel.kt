package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.parser.MangaDataRepository
import org.nekosukuriputo.nekuva.local.data.CacheDir
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager
import org.nekosukuriputo.nekuva.search.domain.MangaSearchRepository
import org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository

/**
 * Data removal (Doki DataCleanupSettingsFragment): clears favicon/pages/HTTP caches, the manga database
 * (orphan GC), cookies, search history and the updates feed. Cache sizes are shown live and recomputed
 * after each clear. Thumbnail cache is handled in the screen via the Coil loader (it owns that dir).
 */
class DataCleanupViewModel(
    private val storage: LocalStorageManager,
    private val httpCache: Cache,
    private val mangaDataRepository: MangaDataRepository,
    private val cookieJar: MutableCookieJar,
    private val searchRepository: MangaSearchRepository,
    private val trackingRepository: TrackingRepository,
) : ViewModel() {

    val faviconsSize = MutableStateFlow(-1L)
    val pagesSize = MutableStateFlow(-1L)
    val httpCacheSize = MutableStateFlow(-1L)

    /** Key of the action currently running (drives a per-row "…" state); null when idle. */
    val busy = MutableStateFlow<String?>(null)

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    init {
        refreshSizes()
    }

    fun refreshSizes() {
        viewModelScope.launch { faviconsSize.value = storage.computeCacheSize(CacheDir.FAVICONS) }
        viewModelScope.launch { pagesSize.value = storage.computeCacheSize(CacheDir.PAGES) }
        viewModelScope.launch {
            httpCacheSize.value = withContext(Dispatchers.IO) { runCatching { httpCache.size() }.getOrDefault(0L) }
        }
    }

    private fun launchAction(key: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = key
            try {
                block()
                _done.tryEmit(Unit)
            } finally {
                busy.value = null
                refreshSizes()
            }
        }
    }

    fun clearFavicons() = launchAction(KEY_FAVICONS) { storage.clearCache(CacheDir.FAVICONS) }
    fun clearPages() = launchAction(KEY_PAGES) { storage.clearCache(CacheDir.PAGES) }
    fun clearHttpCache() = launchAction(KEY_HTTP) { withContext(Dispatchers.IO) { runCatching { httpCache.evictAll() } } }
    fun clearDatabase() = launchAction(KEY_DB) {
        mangaDataRepository.cleanupLocalManga()
        mangaDataRepository.cleanupDatabase()
    }
    fun clearCookies() = launchAction(KEY_COOKIES) { cookieJar.clear() }
    fun clearSearchHistory() = launchAction(KEY_SEARCH) { searchRepository.clearSearchHistory() }
    fun clearUpdatesFeed() = launchAction(KEY_FEED) { trackingRepository.clearLogs() }

    companion object {
        const val KEY_FAVICONS = "favicons"
        const val KEY_PAGES = "pages"
        const val KEY_HTTP = "http"
        const val KEY_DB = "db"
        const val KEY_COOKIES = "cookies"
        const val KEY_SEARCH = "search"
        const val KEY_FEED = "feed"
    }
}
