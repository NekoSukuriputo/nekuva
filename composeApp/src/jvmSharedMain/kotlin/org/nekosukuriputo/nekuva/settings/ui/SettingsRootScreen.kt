package org.nekosukuriputo.nekuva.settings.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.about
import nekuva.composeapp.generated.resources.appearance
import nekuva.composeapp.generated.resources.backup_restore
import nekuva.composeapp.generated.resources.check_for_new_chapters
import nekuva.composeapp.generated.resources.coming_soon
import nekuva.composeapp.generated.resources.downloads
import nekuva.composeapp.generated.resources.reader_settings
import nekuva.composeapp.generated.resources.remote_sources
import nekuva.composeapp.generated.resources.services
import nekuva.composeapp.generated.resources.settings
import nekuva.composeapp.generated.resources.storage_and_network
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootScreen(
    onAppearance: () -> Unit,
    onReader: () -> Unit,
    onStorageNetwork: () -> Unit,
    onDownloads: () -> Unit,
    onTracker: () -> Unit,
    onServices: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onBackClick: () -> Unit,
) {
    val soon = stringResource(Res.string.coming_soon)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsItem(title = stringResource(Res.string.appearance), icon = Icons.Outlined.Palette, onClick = onAppearance)
            SettingsItem(title = stringResource(Res.string.remote_sources), icon = Icons.Outlined.Public, summary = soon, enabled = false)
            SettingsItem(title = stringResource(Res.string.reader_settings), icon = Icons.AutoMirrored.Outlined.MenuBook, onClick = onReader)
            SettingsItem(title = stringResource(Res.string.storage_and_network), icon = Icons.Outlined.Storage, onClick = onStorageNetwork)
            SettingsItem(title = stringResource(Res.string.downloads), icon = Icons.Outlined.Download, onClick = onDownloads)
            SettingsItem(title = stringResource(Res.string.check_for_new_chapters), icon = Icons.Outlined.Notifications, onClick = onTracker)
            SettingsItem(title = stringResource(Res.string.services), icon = Icons.Outlined.Extension, onClick = onServices)
            SettingsItem(title = stringResource(Res.string.backup_restore), icon = Icons.Outlined.Backup, onClick = onBackup)
            SettingsItem(title = stringResource(Res.string.about), icon = Icons.Outlined.Info, onClick = onAbout)
        }
    }
}
