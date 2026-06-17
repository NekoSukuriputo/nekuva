package org.nekosukuriputo.nekuva.scrobbling.discord.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.network.webview.PlatformWebView
import org.nekosukuriputo.nekuva.core.network.webview.WebViewState
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.discord_rpc
import nekuva.composeapp.generated.resources.sign_in

private const val LOGIN_URL = "https://discord.com/login"
private const val TOKEN_SCRIPT = "window.localStorage.token"

/**
 * Logs the user into Discord in the in-app browser and reads the user token from `localStorage`
 * (port of Doki's DiscordTokenWebClient). Reports the token via [onToken] once it appears.
 * Android only in practice (Desktop's WebView does not wire JS evaluation → never fires).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordLoginScreen(
    onToken: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state = remember { WebViewState() }
    val captured = remember { mutableStateOf(false) }

    // After each navigation/load settles, probe localStorage for the token (set once logged in).
    LaunchedEffect(state.currentUrl, state.isLoading) {
        if (captured.value) return@LaunchedEffect
        state.evaluateJs(TOKEN_SCRIPT) { result ->
            val token = result
                ?.replace("\\\"", "")
                ?.removeSurrounding("\"")
                ?.takeUnless { it.isEmpty() || it == "null" }
            if (token != null && !captured.value) {
                captured.value = true
                onToken(token)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.discord_rpc)) },
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
        PlatformWebView(url = LOGIN_URL, state = state, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
