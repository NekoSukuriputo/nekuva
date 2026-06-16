package org.nekosukuriputo.nekuva.settings.ui.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

/** Human-readable byte size; "…" while still computing (negative sentinel). */
internal fun formatBytes(b: Long): String = when {
    b < 0 -> "…"
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    else -> "%.2f GB".format(b / 1024.0 / 1024.0 / 1024.0)
}

/**
 * Data removal (Doki DataCleanupSettingsFragment): one row per cache/store with live size, cleared on tap.
 * Thumbnail cache is the Coil disk cache (cleared via the loader). Webview data + "delete read chapters"
 * are deferred to the browser/download areas (recorded in MIGRATION.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCleanupScreen(
    onBackClick: () -> Unit,
    viewModel: DataCleanupViewModel = koinViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val doneMsg = stringResource(Res.string.done)
    val computing = stringResource(Res.string.computing_)
    LaunchedEffect(Unit) { viewModel.done.collect { snackbar.showSnackbar(doneMsg) } }

    val faviconsSize by viewModel.faviconsSize.collectAsState()
    val pagesSize by viewModel.pagesSize.collectAsState()
    val httpCacheSize by viewModel.httpCacheSize.collectAsState()
    val busy by viewModel.busy.collectAsState()

    // Thumbnail cache is owned by Coil; size + clear go through the loader.
    val context = LocalPlatformContext.current
    val loader = remember(context) { SingletonImageLoader.get(context) }
    var thumbsRefresh by remember { mutableStateOf(0) }
    var thumbsSize by remember { mutableStateOf(-1L) }
    LaunchedEffect(thumbsRefresh) {
        thumbsSize = withContext(Dispatchers.IO) { runCatching { loader.diskCache?.size ?: 0L }.getOrDefault(0L) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.data_removal)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsItem(
                title = stringResource(Res.string.clear_search_history),
                onClick = { viewModel.clearSearchHistory() },
            )
            SettingsItem(
                title = stringResource(Res.string.clear_updates_feed),
                onClick = { viewModel.clearUpdatesFeed() },
            )

            HorizontalDivider()
            SettingsItem(
                title = stringResource(Res.string.clear_thumbs_cache),
                summary = formatBytes(thumbsSize),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { loader.diskCache?.clear() }
                            runCatching { loader.memoryCache?.clear() }
                        }
                        thumbsRefresh++
                        snackbar.showSnackbar(doneMsg)
                    }
                },
            )
            SettingsItem(
                title = stringResource(Res.string.clear_pages_cache),
                summary = if (busy == DataCleanupViewModel.KEY_PAGES) computing else formatBytes(pagesSize),
                onClick = { viewModel.clearPages() },
            )
            SettingsItem(
                title = stringResource(Res.string.clear_network_cache),
                summary = if (busy == DataCleanupViewModel.KEY_HTTP) computing else formatBytes(httpCacheSize),
                onClick = { viewModel.clearHttpCache() },
            )
            // Favicons cache (Nekuva-specific: source icons fetched once and stored).
            SettingsItem(
                title = stringResource(Res.string.source_icons),
                summary = if (busy == DataCleanupViewModel.KEY_FAVICONS) computing else formatBytes(faviconsSize),
                onClick = { viewModel.clearFavicons() },
            )

            HorizontalDivider()
            SettingsItem(
                title = stringResource(Res.string.clear_database),
                summary = stringResource(Res.string.clear_database_summary),
                onClick = { viewModel.clearDatabase() },
            )
            SettingsItem(
                title = stringResource(Res.string.clear_cookies),
                summary = stringResource(Res.string.clear_cookies_summary),
                onClick = { viewModel.clearCookies() },
            )
        }
    }
}
