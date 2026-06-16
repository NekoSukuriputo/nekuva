package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onDataRemoval: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    val imagesProxy by viewModel.imagesProxy.collectAsState()
    val storageUsage by viewModel.storageUsage.collectAsState()

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
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Storage usage meter + Data removal sub-screen (Doki "Storage usage" category).
            SettingsCategoryHeader(stringResource(Res.string.storage_usage))
            val usage = storageUsage
            if (usage == null) {
                SettingsItem(title = stringResource(Res.string.storage_usage), summary = stringResource(Res.string.computing_))
            } else {
                StorageUsageBar(usage)
            }
            SettingsItem(
                title = stringResource(Res.string.data_removal),
                onClick = onDataRemoval,
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
}

/** Segmented storage-usage bar + legend (Doki StorageUsagePreference / SegmentedBarView). */
@Composable
private fun StorageUsageBar(usage: StorageUsage) {
    val savedColor = Color(0xFF3B82F6)   // blue — saved manga
    val pagesColor = Color(0xFFEF4444)   // red — pages cache
    val otherColor = Color(0xFF22C55E)   // green — other cache
    val trackColor = MaterialTheme.colorScheme.surfaceVariant // available
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(trackColor),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (usage.savedManga.percent > 0f) {
                    Box(Modifier.weight(usage.savedManga.percent).fillMaxHeight().background(savedColor))
                }
                if (usage.pagesCache.percent > 0f) {
                    Box(Modifier.weight(usage.pagesCache.percent).fillMaxHeight().background(pagesColor))
                }
                if (usage.otherCache.percent > 0f) {
                    Box(Modifier.weight(usage.otherCache.percent).fillMaxHeight().background(otherColor))
                }
                if (usage.available.percent > 0f) {
                    Spacer(Modifier.weight(usage.available.percent))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StorageLegendRow(savedColor, formatBytes(usage.savedManga.bytes), stringResource(Res.string.saved_manga))
        StorageLegendRow(pagesColor, formatBytes(usage.pagesCache.bytes), stringResource(Res.string.pages_cache))
        StorageLegendRow(otherColor, formatBytes(usage.otherCache.bytes), stringResource(Res.string.other_cache))
        StorageLegendRow(trackColor, formatBytes(usage.available.bytes), stringResource(Res.string.available))
    }
}

@Composable
private fun StorageLegendRow(color: Color, size: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text("$size - $label", style = MaterialTheme.typography.bodyMedium)
    }
}
