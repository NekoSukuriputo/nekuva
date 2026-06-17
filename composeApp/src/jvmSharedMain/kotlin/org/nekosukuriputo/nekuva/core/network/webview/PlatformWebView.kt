package org.nekosukuriputo.nekuva.core.network.webview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Observable state + control surface for [PlatformWebView], shared by the in-app browser and the
 * CloudFlare-resolution screen. The platform actual (Android WebView / Desktop KCEF) writes the
 * observable fields and wires the action lambdas.
 */
class WebViewState {
    var title: String by mutableStateOf("")
    var currentUrl: String by mutableStateOf("")
    var progress: Float by mutableStateOf(0f)
    var isLoading: Boolean by mutableStateOf(true)
    var canGoBack: Boolean by mutableStateOf(false)

    internal var goBackAction: (() -> Unit)? = null
    internal var reloadAction: (() -> Unit)? = null
    internal var evaluateJsAction: ((String, (String?) -> Unit) -> Unit)? = null

    fun goBack() {
        goBackAction?.invoke()
    }

    fun reload() {
        reloadAction?.invoke()
    }

    /**
     * Evaluate [script] in the page and deliver the JSON-encoded result (Discord token capture, Doki's
     * DiscordTokenWebClient). No-op where the engine doesn't wire it (e.g. Desktop).
     */
    fun evaluateJs(script: String, onResult: (String?) -> Unit) {
        evaluateJsAction?.invoke(script, onResult)
    }
}

/**
 * Hosts a real browser engine (Android `WebView` / Desktop KCEF Chromium) rendering [url] and reporting
 * navigation state into [state]. The same engine that backs `evaluateJs`, now visible — used by the
 * in-app browser and CloudFlare captcha screens (UI mirrors Doki's BrowserActivity).
 */
@Composable
expect fun PlatformWebView(
    url: String,
    state: WebViewState,
    modifier: Modifier,
)
