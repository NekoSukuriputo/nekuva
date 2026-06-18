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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

private const val GITHUB_REPO = "https://github.com/NekoSukuriputo/nekuva"
// Doki points the user manual at the Kotatsu guide (url_user_manual); reused here per the migration note.
private const val USER_MANUAL_URL = "https://kotatsu.app/manuals/guides/getting-started/"
private const val APP_VERSION = "1.0"

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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UpdateCheckResult.UpToDate -> snackbar.showSnackbar(upToDate)
                is UpdateCheckResult.Available -> {
                    val result = snackbar.showSnackbar(
                        message = getString(Res.string.new_version_s, event.version.name),
                        actionLabel = "↗",
                    )
                    if (result == SnackbarResult.ActionPerformed) uriHandler.openUri(event.version.url)
                }
            }
        }
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
            // Changelog — disabled in Doki too (TODO there); shown for parity until release notes are wired.
            SettingsItem(
                title = stringResource(Res.string.changelog),
                summary = stringResource(Res.string.changelog_summary),
                icon = Icons.Outlined.History,
                enabled = false,
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
