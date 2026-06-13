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

    val defaultUserAgent: String? by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: AndroidRuntimeException) {
            e.printStackTrace()
            null // WebView probably unavailable
        }
    }

    suspend fun evaluateJs(baseUrl: String?, script: String): String? = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val webView = obtainWebView()
            try {
                if (!baseUrl.isNullOrEmpty()) {
                    // Load the page so the script runs in that origin (location/cookies/DOM), like Doki.
                    suspendCoroutine { cont ->
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
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
