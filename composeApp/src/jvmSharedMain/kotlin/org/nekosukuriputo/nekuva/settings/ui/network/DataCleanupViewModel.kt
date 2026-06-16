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
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.local.data.CacheDir
import org.nekosukuriputo.nekuva.local.data.LocalStorageManager
import org.nekosukuriputo.nekuva.local.domain.DeleteReadChaptersUseCase
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
    private val deleteReadChaptersUseCase: DeleteReadChaptersUseCase,
    private val settings: AppSettings,
) : ViewModel() {

    val faviconsSize = MutableStateFlow(-1L)
    val pagesSize = MutableStateFlow(-1L)
    val httpCacheSize = MutableStateFlow(-1L)
    val searchHistoryCount = MutableStateFlow(-1)
    val feedItemsCount = MutableStateFlow(-1)

    /** Key of the action currently running (drives a per-row "…" state); null when idle. */
    val busy = MutableStateFlow<String?>(null)

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    /** Auto-delete read chapters on app start (Doki chapters_clear_auto). */
    var autoDeleteReadChapters: Boolean
        get() = settings.prefBoolean(AppSettings.KEY_CHAPTERS_CLEAR_AUTO, false)
        set(value) = settings.setPref(AppSettings.KEY_CHAPTERS_CLEAR_AUTO, value)

    val isBrowserDataCleanupEnabled: Boolean get() = true

    init {
        refreshSizes()
    }

    fun refreshSizes() {
        viewModelScope.launch { faviconsSize.value = storage.computeCacheSize(CacheDir.FAVICONS) }
        viewModelScope.launch { pagesSize.value = storage.computeCacheSize(CacheDir.PAGES) }
        viewModelScope.launch {
            httpCacheSize.value = withContext(Dispatchers.IO) { runCatching { httpCache.size() }.getOrDefault(0L) }
        }
        viewModelScope.launch { searchHistoryCount.value = searchRepository.getSearchHistoryCount() }
        viewModelScope.launch { feedItemsCount.value = runCatching { trackingRepository.getLogsCount() }.getOrDefault(0) }
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
    fun clearBrowserData() = launchAction(KEY_WEBVIEW) {
        org.nekosukuriputo.nekuva.core.network.webview.clearBrowserData()
    }
    fun deleteReadChapters() = launchAction(KEY_CHAPTERS) { deleteReadChaptersUseCase() }

    companion object {
        const val KEY_FAVICONS = "favicons"
        const val KEY_PAGES = "pages"
        const val KEY_HTTP = "http"
        const val KEY_DB = "db"
        const val KEY_COOKIES = "cookies"
        const val KEY_SEARCH = "search"
        const val KEY_FEED = "feed"
        const val KEY_WEBVIEW = "webview"
        const val KEY_CHAPTERS = "chapters"
    }
}
