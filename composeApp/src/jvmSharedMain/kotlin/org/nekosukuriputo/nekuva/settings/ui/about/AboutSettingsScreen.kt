package org.nekosukuriputo.nekuva.settings.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.extensions.ExtState
import org.nekosukuriputo.nekuva.core.extensions.ExtensionManager
import org.nekosukuriputo.nekuva.core.extensions.pickExtensionJar
import org.nekosukuriputo.nekuva.core.extensions.supportsExtensionImport
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

private const val GITHUB_REPO = "https://github.com/NekoSukuriputo/nekuva"
// Doki points the user manual at the Kotatsu guide (url_user_manual); reused here per the migration note.
private const val USER_MANUAL_URL = "https://kotatsu.app/manuals/guides/getting-started/"
private const val APP_VERSION = org.nekosukuriputo.nekuva.core.AppInfo.VERSION_NAME

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: AboutSettingsViewModel = koinViewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val isChecking by viewModel.isChecking.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val checking = stringResource(Res.string.check_for_updates)
    val upToDate = stringResource(Res.string.youre_using_the_latest_version)

    // Runtime extensions (updatable parser bundle): status + check/import actions.
    val extManager = koinInject<ExtensionManager>()
    val extState by extManager.state.collectAsState()
    // Dot on the "Update extensions" row when a newer ext bundle is published (Point 4).
    val extUpdateAvailable by extManager.updateAvailable.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            extManager.loadInstalled()
            runCatching { extManager.checkForUpdate() }
        }
    }
    val extSummary = when (val s = extState) {
        is ExtState.Working -> "${stringResource(Res.string.loading)}…"
        is ExtState.Installed -> {
            val n = stringResource(Res.string.extensions_sources_d, s.sourceCount)
            if (s.version.isNotBlank() && s.version != "imported") "$n • ${s.version}" else n
        }
        is ExtState.UpToDate -> upToDate
        is ExtState.Error -> s.message
        is ExtState.Idle -> stringResource(Res.string.extensions_using_builtin)
    }

    // When the manual check finds a newer release, show the full update dialog (Doki AppUpdateActivity) —
    // download + install on Android, open the release page on Desktop — instead of a bare toast.
    var updateAvailable by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<org.nekosukuriputo.nekuva.core.github.AppVersion?>(null)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UpdateCheckResult.UpToDate -> snackbar.showSnackbar(upToDate)
                is UpdateCheckResult.Available -> updateAvailable = event.version
            }
        }
    }
    updateAvailable?.let { version ->
        AppUpdateDialog(version = version, onDismiss = { updateAvailable = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.about)) },
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
            // App version → tap to check GitHub for a newer release (Doki app_version + AppUpdateRepository).
            SettingsItem(
                title = "${stringResource(Res.string.app_name)} $APP_VERSION",
                summary = if (isChecking) "$checking…" else checking,
                icon = Icons.Outlined.Info,
                enabled = !isChecking,
                onClick = { viewModel.checkForUpdates(APP_VERSION) },
            )
            // Update extensions: download the latest parser bundle from the nekuva-exts release (no app
            // rebuild). Loaded sources will plug into Explore once the runtime registry lands.
            SettingsItem(
                title = stringResource(Res.string.update_extensions),
                summary = extSummary,
                icon = Icons.Outlined.Extension,
                enabled = extState !is ExtState.Working,
                onClick = { scope.launch { extManager.checkAndUpdate() } },
                // Dot indicator when a newer ext bundle exists (matches the search-box ext-update icon).
                trailing = if (extUpdateAvailable) {
                    { androidx.compose.material3.Badge() }
                } else {
                    null
                },
            )
            if (supportsExtensionImport) {
                SettingsItem(
                    title = stringResource(Res.string.import_extension),
                    icon = Icons.Outlined.FileDownload,
                    enabled = extState !is ExtState.Working,
                    onClick = {
                        scope.launch {
                            val picked = pickExtensionJar()
                            if (picked != null) extManager.installFromFile(picked)
                        }
                    },
                )
            }
            // Changelog → GitHub releases page (Doki leaves this disabled; Nekuva already publishes releases,
            // so open them rather than show a dead "coming soon").
            SettingsItem(
                title = stringResource(Res.string.changelog),
                summary = stringResource(Res.string.changelog_summary),
                icon = Icons.Outlined.History,
                onClick = { uriHandler.openUri("$GITHUB_REPO/releases") },
            )
            // User manual → Kotatsu guide (Doki url_user_manual).
            SettingsItem(
                title = stringResource(Res.string.user_manual),
                summary = USER_MANUAL_URL,
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = { uriHandler.openUri(USER_MANUAL_URL) },
            )
            SettingsItem(
                title = stringResource(Res.string.source_code),
                summary = GITHUB_REPO,
                icon = Icons.Outlined.Code,
                onClick = { uriHandler.openUri(GITHUB_REPO) },
            )
            // Translate this app — disabled in Doki (no Nekuva Weblate yet).
            SettingsItem(
                title = stringResource(Res.string.about_app_translation_summary),
                summary = stringResource(Res.string.coming_soon),
                icon = Icons.Outlined.Translate,
                enabled = false,
            )
        }
    }
}
