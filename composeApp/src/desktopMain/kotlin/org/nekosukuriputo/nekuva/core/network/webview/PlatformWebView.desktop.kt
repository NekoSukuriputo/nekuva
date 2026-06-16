package org.nekosukuriputo.nekuva.core.network.webview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import dev.datlag.kcef.KCEFBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.network.webview.adblock.AdBlock

@Composable
actual fun PlatformWebView(
    url: String,
    state: WebViewState,
    modifier: Modifier,
) {
    var browser by remember { mutableStateOf<KCEFBrowser?>(null) }
    val scope = rememberCoroutineScope()
    val adBlock = koinInject<AdBlock>()

    DisposableEffect(url) {
        val job = scope.launch(Dispatchers.IO) {
            val client = KcefManager.newClient() ?: return@launch
            // Ad blocking (Doki adblock): cancel matched sub-resource requests before they load.
            client.addRequestHandler(object : CefRequestHandlerAdapter() {
                override fun getResourceRequestHandler(
                    b: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef?,
                ): CefResourceRequestHandler {
                    return object : CefResourceRequestHandlerAdapter() {
                        override fun onBeforeResourceLoad(rb: CefBrowser?, rf: CefFrame?, req: CefRequest?): Boolean {
                            val u = req?.url ?: return false
                            return !adBlock.shouldLoadUrl(u, state.currentUrl.ifEmpty { null })
                        }
                    }
                }
            })
            client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onTitleChange(b: CefBrowser?, title: String?) {
                    title?.let { state.title = it }
                }

                override fun onAddressChange(b: CefBrowser?, frame: CefFrame?, u: String?) {
                    u?.let { state.currentUrl = it }
                }
            })
            client.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(b: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                    state.isLoading = isLoading
                    state.canGoBack = canGoBack
                    state.progress = if (isLoading) 0.5f else 1f
                }
            })
            val b = client.createBrowser(url, CefRendering.DEFAULT, false)
            state.goBackAction = { if (b.canGoBack()) b.goBack() }
            state.reloadAction = { b.reload() }
            browser = b
        }
        onDispose {
            job.cancel()
            browser?.let { b -> runCatching { b.close(true) } }
        }
    }

    val current = browser
    if (current != null) {
        SwingPanel(modifier = modifier, factory = { current.uiComponent })
    } else {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
