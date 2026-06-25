package org.nekosukuriputo.nekuva.core.network.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.network.webview.adblock.AdBlock
import org.nekosukuriputo.nekuva.parsers.network.UserAgents
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(
    url: String,
    state: WebViewState,
    modifier: Modifier,
) {
    val adBlock = koinInject<AdBlock>()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    // Use the SAME User-Agent as OkHttp (the parser). cf_clearance is bound to the UA, so if
                    // the challenge is solved with a different UA the parser's request is rejected and the
                    // "CAPTCHA required" wall comes straight back.
                    userAgentString = UserAgents.FIREFOX_MOBILE
                }
                webViewClient = object : WebViewClient() {
                    // Capture custom-scheme redirects (e.g. OAuth `nekuva://oauth?code=...`) which the engine
                    // can't navigate to — surface the URL so the OAuth screen reads the code, and swallow it.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        state.currentUrl = target
                        return !(target.startsWith("http://") || target.startsWith("https://"))
                    }

                    override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                        state.isLoading = true
                        u?.let { state.currentUrl = it }
                    }

                    override fun onPageFinished(view: WebView, u: String?) {
                        state.isLoading = false
                        state.canGoBack = view.canGoBack()
                        u?.let { state.currentUrl = it }
                    }

                    override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                        state.canGoBack = view.canGoBack()
                    }

                    // Ad blocking (Doki adblock): block matched requests with an empty response. Uses the
                    // main-thread-updated state.currentUrl as the base (WebView.getUrl() is unsafe off-thread).
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        return if (!adBlock.shouldLoadUrl(request.url.toString(), state.currentUrl.ifEmpty { null })) {
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        } else {
                            null
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        state.progress = newProgress / 100f
                    }

                    override fun onReceivedTitle(view: WebView, title: String?) {
                        title?.let { state.title = it }
                    }
                }
                state.goBackAction = { if (canGoBack()) goBack() }
                state.reloadAction = { reload() }
                state.evaluateJsAction = { script, onResult -> evaluateJavascript(script) { onResult(it) } }
                loadUrl(url)
            }
        },
    )
}
