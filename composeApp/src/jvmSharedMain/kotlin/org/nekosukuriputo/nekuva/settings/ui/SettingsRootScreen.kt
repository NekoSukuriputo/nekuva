package org.nekosukuriputo.nekuva.settings.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.about
import nekuva.composeapp.generated.resources.appearance
import nekuva.composeapp.generated.resources.backup_restore
import nekuva.composeapp.generated.resources.check_for_new_chapters
import nekuva.composeapp.generated.resources.downloads
import nekuva.composeapp.generated.resources.reader_settings
import nekuva.composeapp.generated.resources.remote_sources
import nekuva.composeapp.generated.resources.search
import nekuva.composeapp.generated.resources.services
import nekuva.composeapp.generated.resources.settings
import nekuva.composeapp.generated.resources.storage_and_network
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

private data class SettingsEntry(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootScreen(
    onAppearance: () -> Unit,
    onRemoteSources: () -> Unit = {},
    onReader: () -> Unit,
    onStorageNetwork: () -> Unit,
    onDownloads: () -> Unit,
    onTracker: () -> Unit,
    onServices: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onBackClick: () -> Unit,
) {
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Category index (Doki settings search) — filtered by title as the user types.
    val entries = listOf(
        SettingsEntry(stringResource(Res.string.appearance), Icons.Outlined.Palette, onAppearance),
        SettingsEntry(stringResource(Res.string.remote_sources), Icons.Outlined.Public, onRemoteSources),
        SettingsEntry(stringResource(Res.string.reader_settings), Icons.AutoMirrored.Outlined.MenuBook, onReader),
        SettingsEntry(stringResource(Res.string.storage_and_network), Icons.Outlined.Storage, onStorageNetwork),
        SettingsEntry(stringResource(Res.string.downloads), Icons.Outlined.Download, onDownloads),
        SettingsEntry(stringResource(Res.string.check_for_new_chapters), Icons.Outlined.Notifications, onTracker),
        SettingsEntry(stringResource(Res.string.services), Icons.Outlined.Extension, onServices),
        SettingsEntry(stringResource(Res.string.backup_restore), Icons.Outlined.Backup, onBackup),
        SettingsEntry(stringResource(Res.string.about), Icons.Outlined.Info, onAbout),
    )
    val visible = if (query.isBlank()) entries else entries.filter { it.title.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        // Compact rounded search field (Doki settings search) — not a bulky full-height box.
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = { Text(stringResource(Res.string.search)) },
                        )
                    } else {
                        Text(stringResource(Res.string.settings))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) { searchActive = false; query = "" } else onBackClick()
                    }) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.search))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            for (entry in visible) {
                SettingsItem(title = entry.title, icon = entry.icon, onClick = entry.onClick)
            }
        }
    }
}
