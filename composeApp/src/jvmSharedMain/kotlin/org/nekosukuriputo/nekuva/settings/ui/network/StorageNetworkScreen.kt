package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.launch
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.IndexListPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageNetworkScreen(
    viewModel: StorageNetworkViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onProxy: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    val imagesProxy by viewModel.imagesProxy.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current
    var confirmClearCookies by remember { mutableStateOf(false) }

    val doneMsg = stringResource(Res.string.done)
    LaunchedEffect(Unit) {
        viewModel.onCookiesCleared.collect { snackbarHostState.showSnackbar(doneMsg) }
    }

    val networkPolicy = listOf(
        stringResource(Res.string.always),
        stringResource(Res.string.only_using_wifi),
        stringResource(Res.string.never),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.storage_and_network)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Data removal (clear caches). Storage-usage meter is deferred (needs size computation).
            SettingsCategoryHeader(stringResource(Res.string.data_removal))
            SettingsItem(
                title = stringResource(Res.string.clear_thumbs_cache),
                onClick = {
                    scope.launch {
                        val loader = SingletonImageLoader.get(platformContext)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        snackbarHostState.showSnackbar(doneMsg)
                    }
                },
            )
            SettingsItem(
                title = stringResource(Res.string.clear_cookies),
                summary = stringResource(Res.string.clear_cookies_summary),
                onClick = { confirmClearCookies = true },
            )

            IndexListPref(settings, AppSettings.KEY_PREFETCH_CONTENT, stringResource(Res.string.prefetch_content), networkPolicy, 0)
            IndexListPref(settings, AppSettings.KEY_PAGES_PRELOAD, stringResource(Res.string.preload_pages), networkPolicy, 1)

            // Proxy sub-screen (type/address/port/auth/test) — Doki ProxySettingsFragment.
            SettingsItem(title = stringResource(Res.string.proxy), onClick = onProxy)

            IndexListPref(
                settings, AppSettings.KEY_DOH, stringResource(Res.string.dns_over_https),
                listOf(stringResource(Res.string.disabled), "Google", "CloudFlare", "AdGuard", "0ms"), 0,
            )

            // Image optimization proxy (live — RealImageProxyInterceptor reacts immediately)
            SettingsSingleChoice(
                title = stringResource(Res.string.images_proxy_title),
                options = listOf(stringResource(Res.string.none) to -1, "wsrv.nl" to 0, "0ms.dev" to 1),
                selected = imagesProxy,
                onSelect = { viewModel.setImagesProxy(it) },
            )

            BoolPref(settings, AppSettings.KEY_SSL_BYPASS, stringResource(Res.string.ignore_ssl_errors), stringResource(Res.string.ignore_ssl_errors_summary), false)
            BoolPref(settings, AppSettings.KEY_OFFLINE_DISABLED, stringResource(Res.string.disable_connectivity_check), stringResource(Res.string.disable_connectivity_check_summary), false)
            BoolPref(settings, AppSettings.KEY_ADBLOCK, stringResource(Res.string.adblock), stringResource(Res.string.adblock_summary), false)
        }
    }

    if (confirmClearCookies) {
        AlertDialog(
            onDismissRequest = { confirmClearCookies = false },
            title = { Text(stringResource(Res.string.clear_cookies)) },
            text = { Text(stringResource(Res.string.clear_cookies_summary)) },
            confirmButton = {
                TextButton(onClick = { confirmClearCookies = false; viewModel.clearCookies() }) {
                    Text(stringResource(Res.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCookies = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
