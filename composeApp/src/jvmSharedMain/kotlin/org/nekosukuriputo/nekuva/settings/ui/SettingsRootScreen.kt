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
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

private data class SettingsEntry(val title: String, val icon: ImageVector, val onClick: () -> Unit)

/** A single searchable preference (Doki SettingsItem): its title, a breadcrumb of where it lives, and
 *  the action that opens the screen containing it. */
private data class SettingsSearchEntry(val title: String, val breadcrumb: String, val onNavigate: () -> Unit)

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

    // Top-level categories (shown when not searching).
    val catAppearance = stringResource(Res.string.appearance)
    val catSources = stringResource(Res.string.remote_sources)
    val catReader = stringResource(Res.string.reader_settings)
    val catStorage = stringResource(Res.string.storage_and_network)
    val catDownloads = stringResource(Res.string.downloads)
    val catTracker = stringResource(Res.string.check_for_new_chapters)
    val catServices = stringResource(Res.string.services)
    val catBackup = stringResource(Res.string.backup_restore)
    val catAbout = stringResource(Res.string.about)

    val entries = listOf(
        SettingsEntry(catAppearance, Icons.Outlined.Palette, onAppearance),
        SettingsEntry(catSources, Icons.Outlined.Public, onRemoteSources),
        SettingsEntry(catReader, Icons.AutoMirrored.Outlined.MenuBook, onReader),
        SettingsEntry(catStorage, Icons.Outlined.Storage, onStorageNetwork),
        SettingsEntry(catDownloads, Icons.Outlined.Download, onDownloads),
        SettingsEntry(catTracker, Icons.Outlined.Notifications, onTracker),
        SettingsEntry(catServices, Icons.Outlined.Extension, onServices),
        SettingsEntry(catBackup, Icons.Outlined.Backup, onBackup),
        SettingsEntry(catAbout, Icons.Outlined.Info, onAbout),
    )

    // Deep search index (Doki SettingsSearchHelper): every individual preference, tagged with the
    // category it lives in. Searching a setting name (e.g. "proxy", "clear cache", "double page")
    // lands on the screen that holds it — not just the category. Sub-screen prefs (Proxy, Data removal)
    // navigate to their parent screen, which links straight to them.
    val subProxy = stringResource(Res.string.proxy)
    val subDataRemoval = stringResource(Res.string.data_removal)
    fun bc(vararg parts: String) = parts.joinToString(" › ")
    val index = buildList {
        // Appearance
        add(SettingsSearchEntry(stringResource(Res.string.color_theme), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.theme), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.black_dark_theme), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.language), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.list_mode), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.grid_size), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.main_screen_sections), catAppearance, onAppearance))
        add(SettingsSearchEntry(stringResource(Res.string.protect_application), catAppearance, onAppearance))
        // Reader
        add(SettingsSearchEntry(stringResource(Res.string.default_mode), catReader, onReader))
        add(SettingsSearchEntry(stringResource(Res.string.default_webtoon_zoom_out), catReader, onReader))
        add(SettingsSearchEntry(stringResource(Res.string.reader_actions), catReader, onReader))
        // Sources / remote
        add(SettingsSearchEntry(stringResource(Res.string.sort_order), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.show_in_grid_view), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.manage_sources), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.enable_all_sources), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.sources_catalog), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.disable_nsfw), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.incognito_for_nsfw), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.tags_warnings), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.mirror_switching), catSources, onRemoteSources))
        add(SettingsSearchEntry(stringResource(Res.string.handle_links), catSources, onRemoteSources))
        // Storage & network (incl. Proxy + Data removal sub-screens)
        add(SettingsSearchEntry(stringResource(Res.string.storage_usage), catStorage, onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.data_removal), catStorage, onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.proxy), catStorage, onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.images_proxy_title), catStorage, onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.address), bc(catStorage, subProxy), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.port), bc(catStorage, subProxy), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.test_connection), bc(catStorage, subProxy), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_search_history), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_updates_feed), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_thumbs_cache), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_pages_cache), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_network_cache), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_database), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_cookies), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.clear_browser_data), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.delete_read_chapters), bc(catStorage, subDataRemoval), onStorageNetwork))
        add(SettingsSearchEntry(stringResource(Res.string.delete_read_chapters_auto), bc(catStorage, subDataRemoval), onStorageNetwork))
        // Downloads
        add(SettingsSearchEntry(stringResource(Res.string.specify_directory), catDownloads, onDownloads))
        add(SettingsSearchEntry(stringResource(Res.string.preferred_download_format), catDownloads, onDownloads))
        add(SettingsSearchEntry(stringResource(Res.string.download_over_cellular), catDownloads, onDownloads))
        add(SettingsSearchEntry(stringResource(Res.string.default_page_save_dir), catDownloads, onDownloads))
        // Tracker / new chapters
        add(SettingsSearchEntry(stringResource(Res.string.favourites_categories), catTracker, onTracker))
        add(SettingsSearchEntry(stringResource(Res.string.notifications_settings), catTracker, onTracker))
        add(SettingsSearchEntry(stringResource(Res.string.download_new_chapters), catTracker, onTracker))
        add(SettingsSearchEntry(stringResource(Res.string.disable_battery_optimization), catTracker, onTracker))
        // Services
        add(SettingsSearchEntry(stringResource(Res.string.sync), catServices, onServices))
        add(SettingsSearchEntry(stringResource(Res.string.suggestions), catServices, onServices))
        add(SettingsSearchEntry(stringResource(Res.string.discord_token), catServices, onServices))
        // About
        add(SettingsSearchEntry(stringResource(Res.string.changelog), catAbout, onAbout))
        add(SettingsSearchEntry(stringResource(Res.string.user_manual), catAbout, onAbout))
        add(SettingsSearchEntry(stringResource(Res.string.source_code), catAbout, onAbout))
    }

    // When searching, match BOTH category names and individual preference titles (Doki deep search).
    val categoryMatches = if (query.isBlank()) entries else entries.filter { it.title.contains(query, ignoreCase = true) }
    val prefMatches = if (query.isBlank()) emptyList() else index.filter { it.title.contains(query, ignoreCase = true) }

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
            // Categories first (so a query like "reader" shows the category then its preferences).
            for (entry in categoryMatches) {
                SettingsItem(title = entry.title, icon = entry.icon, onClick = entry.onClick)
            }
            // Then individual preference matches, with their breadcrumb as the summary (Doki deep search).
            for (pref in prefMatches) {
                SettingsItem(title = pref.title, summary = pref.breadcrumb, onClick = pref.onNavigate)
            }
        }
    }
}
