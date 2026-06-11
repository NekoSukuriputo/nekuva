package org.nekosukuriputo.nekuva.settings.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.about
import nekuva.composeapp.generated.resources.app_name
import nekuva.composeapp.generated.resources.check_for_updates
import nekuva.composeapp.generated.resources.source_code
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

private const val GITHUB_REPO = "https://github.com/NekoSukuriputo/nekuva"
private const val GITHUB_RELEASES = "https://github.com/NekoSukuriputo/nekuva/releases"
private const val APP_VERSION = "1.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onBackClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
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
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsItem(
                title = "${stringResource(Res.string.app_name)} $APP_VERSION",
                summary = stringResource(Res.string.check_for_updates),
                icon = Icons.Outlined.Info,
                onClick = { uriHandler.openUri(GITHUB_RELEASES) },
            )
            SettingsItem(
                title = stringResource(Res.string.source_code),
                summary = GITHUB_REPO,
                icon = Icons.Outlined.Code,
                onClick = { uriHandler.openUri(GITHUB_REPO) },
            )
        }
    }
}
