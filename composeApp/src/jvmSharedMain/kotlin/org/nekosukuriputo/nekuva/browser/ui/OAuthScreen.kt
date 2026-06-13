package org.nekosukuriputo.nekuva.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.network.webview.PlatformWebView
import org.nekosukuriputo.nekuva.core.network.webview.WebViewState
import org.nekosukuriputo.nekuva.scrobbling.common.ScrobblerConfig
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.sign_in

/**
 * Hosts an OAuth authorize page in the in-app browser and reports the `code` once the page redirects
 * to [ScrobblerConfig.REDIRECT_URI]. Used by scrobbler login (Doki's ScrobblerAuthHelper equivalent).
 *
 * Note: capture relies on the browser surfacing the redirect URL. If the custom `nekuva://` scheme is
 * swallowed by the engine without a URL change, this needs a navigation-intercept (shouldOverrideUrl /
 * CefRequestHandler) — to refine once a real client id is available to test against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthScreen(
    authUrl: String,
    onCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state = remember { WebViewState() }

    LaunchedEffect(state.currentUrl) {
        val url = state.currentUrl
        if (url.startsWith(ScrobblerConfig.REDIRECT_URI)) {
            val code = url.substringAfter("code=", "").substringBefore("&").takeIf { it.isNotEmpty() }
            if (code != null) onCode(code)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.sign_in)) },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                    },
                )
                if (state.isLoading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        PlatformWebView(url = authUrl, state = state, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
