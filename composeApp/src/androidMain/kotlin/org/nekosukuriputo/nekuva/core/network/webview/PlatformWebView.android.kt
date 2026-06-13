package org.nekosukuriputo.nekuva.core.network.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(
    url: String,
    state: WebViewState,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                }
                webViewClient = object : WebViewClient() {
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
                loadUrl(url)
            }
        },
    )
}
