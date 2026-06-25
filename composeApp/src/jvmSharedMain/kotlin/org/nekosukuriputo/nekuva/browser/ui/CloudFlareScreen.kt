package org.nekosukuriputo.nekuva.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.network.cookies.MutableCookieJar
import org.nekosukuriputo.nekuva.core.network.cookies.syncBrowserCookies
import org.nekosukuriputo.nekuva.core.network.webview.PlatformWebView
import org.nekosukuriputo.nekuva.core.network.webview.WebViewState
import org.nekosukuriputo.nekuva.parsers.network.CloudFlareHelper
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.captcha_required
import nekuva.composeapp.generated.resources.try_again

/**
 * Solve a CloudFlare challenge: shows the protected [url] in the real browser engine and polls for the
 * `cf_clearance` cookie. When it appears (synced into the shared OkHttp jar), [onResolved] fires so the
 * caller can retry the failed request. Mirrors Doki's CloudFlareActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudFlareScreen(
    url: String,
    onResolved: () -> Unit,
    onCancel: () -> Unit,
    userAgent: String? = null,
) {
    val state = remember { WebViewState() }
    val cookieJar = koinInject<MutableCookieJar>()
    val initialClearance = remember(url) {
        runCatching { CloudFlareHelper.getClearanceCookie(cookieJar, url) }.getOrNull()
    }
    var resolved by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        while (!resolved) {
            delay(800)
            runCatching { syncBrowserCookies(url, cookieJar) }
            val clearance = runCatching { CloudFlareHelper.getClearanceCookie(cookieJar, url) }.getOrNull()
            if (clearance != null && clearance != initialClearance) {
                resolved = true
                onResolved()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.captcha_required),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { state.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.try_again))
                        }
                    },
                )
                if (state.isLoading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        PlatformWebView(
            url = url,
            state = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            userAgent = userAgent,
        )
    }
}
