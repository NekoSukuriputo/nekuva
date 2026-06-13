package org.nekosukuriputo.nekuva.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.network.webview.PlatformWebView
import org.nekosukuriputo.nekuva.core.network.webview.WebViewState
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.loading_
import nekuva.composeapp.generated.resources.open_in_browser
import nekuva.composeapp.generated.resources.try_again

/**
 * In-app browser (Doki's BrowserActivity): a real engine [PlatformWebView] under a toolbar showing the
 * page title + host, with reload and "open in external browser". The toolbar back arrow navigates the
 * page back first, then leaves the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    url: String,
    onBack: () -> Unit,
    title: String? = null,
) {
    val state = remember { WebViewState() }
    val uriHandler = LocalUriHandler.current
    val loadingText = stringResource(Res.string.loading_)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.title.ifEmpty { title ?: loadingText },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val host = remember(state.currentUrl) { hostOf(state.currentUrl.ifEmpty { url }) }
                            Text(
                                text = host,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (state.canGoBack) state.goBack() else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { state.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.try_again))
                        }
                        IconButton(onClick = {
                            runCatching { uriHandler.openUri(state.currentUrl.ifEmpty { url }) }
                        }) {
                            Icon(
                                Icons.Default.OpenInBrowser,
                                contentDescription = stringResource(Res.string.open_in_browser),
                            )
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
        )
    }
}

private fun hostOf(url: String): String = runCatching {
    java.net.URI(url).host?.removePrefix("www.").orEmpty()
}.getOrDefault("").ifEmpty { url }
