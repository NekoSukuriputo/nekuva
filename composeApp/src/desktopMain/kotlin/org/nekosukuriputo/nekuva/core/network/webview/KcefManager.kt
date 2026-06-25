package org.nekosukuriputo.nekuva.core.network.webview

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.browser.CefRendering
import org.nekosukuriputo.nekuva.parsers.network.UserAgents
import java.io.File

/**
 * The single Chrome UA pinned on the Desktop browser engine. KCEF's own native UA isn't available before
 * init completes, and OkHttp's default UA (AppMangaLoaderContext.desktop) must equal the browser UA so a
 * solved CloudFlare `cf_clearance` (UA-bound) is accepted by the parser's OkHttp requests. A Chrome-family
 * UA also keeps the engine fingerprint consistent so challenges pass normally instead of looping.
 */
const val DESKTOP_USER_AGENT: String = UserAgents.CHROME_WINDOWS

/**
 * Desktop embedded-Chromium engine (KCEF/JCEF) — the Desktop counterpart of Android's WebView.
 * Powers `evaluateJs` (running source JavaScript / passing CloudFlare) and the in-app browser.
 *
 * KCEF downloads its native Chromium bundle (~150 MB) on first launch into `~/.nekuva/kcef`; until
 * that completes, [evaluateJs] returns null so JS-dependent sources fail gracefully (the rest of the
 * app keeps working). [state] exposes progress for a UI.
 */
object KcefManager {

    sealed interface State {
        data object Idle : State
        data class Downloading(val percent: Float) : State
        data object Initializing : State
        data object Ready : State
        data class Failed(val error: Throwable?) : State
    }

    private val installDir = File(System.getProperty("user.home"), ".nekuva/kcef")

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var client: KCEFClient? = null
    private var browser: KCEFBrowser? = null
    @Volatile private var started = false

    /** Begin (idempotent) the one-time native download + initialization in the background. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            runCatching {
                _state.value = State.Initializing
                KCEF.init(
                    builder = {
                        installDir(installDir)
                        // Force GPU-accelerated rasterization so the in-app browser scrolls smoothly.
                        // CEF can fall back to software rendering on some Desktop GPUs, which makes the
                        // embedded page visibly laggy; these flags push compositing/raster onto the GPU.
                        addArgs(
                            "--enable-gpu-rasterization",
                            "--ignore-gpu-blocklist",
                            "--enable-zero-copy",
                        )
                        progress {
                            onDownloading { pct -> _state.value = State.Downloading(pct.coerceIn(0f, 100f)) }
                            onInitialized { _state.value = State.Ready }
                        }
                        settings {
                            cachePath = File(installDir, "cache").absolutePath
                            // Pin the browser UA to match OkHttp's default (cf_clearance is UA-bound).
                            userAgent = DESKTOP_USER_AGENT
                        }
                    },
                    onError = { e -> _state.value = State.Failed(e) },
                    onRestartRequired = {
                        _state.value = State.Failed(IllegalStateException("App restart required to finish browser setup"))
                    },
                )
                if (_state.value !is State.Failed) {
                    _state.value = State.Ready
                }
            }.onFailure { e ->
                _state.value = State.Failed(e)
                e.printStackTrace()
            }
        }
    }

    private suspend fun awaitReady(): Boolean {
        if (!started) start()
        return state.first { it is State.Ready || it is State.Failed } is State.Ready
    }

    suspend fun evaluateJs(baseUrl: String?, script: String): String? = mutex.withLock {
        if (!awaitReady()) return null
        val b = obtainBrowser() ?: return null
        if (!baseUrl.isNullOrEmpty()) {
            // Set the document origin (location/cookies) like Android's loadDataWithBaseURL.
            b.loadHtml(" ", baseUrl)
            withTimeoutOrNull(5_000) {
                while (b.isLoading) {
                    delay(20)
                }
            }
        }
        b.evaluateJavaScript(script)?.takeUnless { it == "null" }
    }

    private suspend fun obtainBrowser(): KCEFBrowser? {
        browser?.let { return it }
        val c = client ?: KCEF.newClient().also { client = it }
        return c.createBrowser(KCEFBrowser.BLANK_URI, CefRendering.DEFAULT, false).also { browser = it }
    }

    /** A fresh client for the in-app browser / CloudFlare screen (its own browser lifecycle). */
    suspend fun newClient(): KCEFClient? = if (awaitReady()) KCEF.newClient() else null
}
