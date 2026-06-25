package org.nekosukuriputo.nekuva.core.network.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AndroidRuntimeException
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.parsers.network.UserAgents
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Runs JavaScript in a hidden [WebView] — the Android implementation of `MangaLoaderContext.evaluateJs`,
 * ported from Doki's WebViewExecutor. A single WebView instance is cached and reset between calls; all
 * access is serialized (one eval at a time) and happens on the main thread.
 */
object WebViewExecutor {

    private val context: Context get() = GlobalContext.get().get()
    private var webViewCached: WeakReference<WebView>? = null
    private val mutex = Mutex()

    // Match OkHttp's UA (the parser's getDefaultUserAgent = UserAgents.FIREFOX_MOBILE) so a cf_clearance
    // earned by the JS-challenge WebView is valid for the parser's requests too (cf_clearance is UA-bound).
    val defaultUserAgent: String = UserAgents.FIREFOX_MOBILE

    suspend fun evaluateJs(baseUrl: String?, script: String): String? = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val webView = obtainWebView()
            try {
                if (!baseUrl.isNullOrEmpty()) {
                    // Load the page so the script runs in that origin (location/cookies/DOM), like Doki.
                    suspendCoroutine { cont ->
                        webView.webViewClient = object : WebViewClient() {
                            // onPageFinished can fire more than once per load (redirects / sub-frames). Guard so
                            // the continuation is resumed exactly once — else "IllegalStateException: Already
                            // resumed" crashes the app (e.g. fast-scrolling Explore fires many JS-source loads).
                            // Mirror Doki: detach the client before resuming so later callbacks are no-ops.
                            private var resumed = false
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (resumed) return
                                resumed = true
                                view?.webViewClient = WebViewClient()
                                cont.resume(Unit)
                            }
                        }
                        webView.loadDataWithBaseURL(baseUrl, " ", "text/html", null, null)
                    }
                }
                suspendCoroutine { cont ->
                    webView.evaluateJavascript(script) { result ->
                        cont.resume(result?.takeUnless { it == "null" })
                    }
                }
            } finally {
                webView.reset()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun obtainWebView(): WebView = withContext(Dispatchers.Main.immediate) {
        webViewCached?.get() ?: WebView(context).also { wv ->
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                defaultUserAgent?.let { userAgentString = it }
            }
            webViewCached = WeakReference(wv)
            wv.resumeTimers()
        }
    }

    private fun WebView.reset() {
        stopLoading()
        webViewClient = WebViewClient()
        defaultUserAgent?.let { settings.userAgentString = it }
        loadDataWithBaseURL(null, " ", "text/html", null, null)
        clearHistory()
    }
}
