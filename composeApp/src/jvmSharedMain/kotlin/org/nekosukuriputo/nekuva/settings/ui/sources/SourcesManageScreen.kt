package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.i18n.localeDisplayName
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.SourceFaviconImage
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

private fun title(source: org.nekosukuriputo.nekuva.core.model.MangaSourceInfo): String =
    (source.mangaSource as? MangaParserSource)?.title ?: source.name

private fun prettify(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Manage enabled sources (Doki SourcesManageFragment): search, add (catalog), pin, reorder, disable,
 *  + overflow (disable NSFW / disable all). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesManageScreen(
    onBackClick: () -> Unit,
    onCatalog: () -> Unit,
    onSourceSettings: (String) -> Unit = {},
    viewModel: SourcesManageViewModel = koinViewModel(),
) {
    val sources by viewModel.sources.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }
    var noNsfw by remember { mutableStateOf(viewModel.isNsfwDisabled) }
    val shown = remember(sources, query) {
        if (query.isBlank()) sources else sources.filter { title(it).contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(Res.string.search)) },
                        )
                    } else {
                        Text(stringResource(Res.string.manage_sources))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (searchActive) { searchActive = false; query = "" } else onBackClick() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.search))
                        }
                        IconButton(onClick = onCatalog) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.sources_catalog))
                        }
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.disable_nsfw)) },
                                trailingIcon = { Switch(checked = noNsfw, onCheckedChange = null) },
                                onClick = { noNsfw = !noNsfw; viewModel.isNsfwDisabled = noNsfw },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.disable_all)) },
                                onClick = { viewModel.disableAll(); overflow = false },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (shown.isEmpty()) {
            EmptyState(message = stringResource(Res.string.no_manga_sources), modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(shown, key = { _, s -> s.name }) { index, source ->
                val parser = source.mangaSource as? MangaParserSource
                var menu by remember(source.name) { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSourceSettings(source.name) }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceFaviconImage(
                        sourceName = source.name,
                        displayName = title(source),
                        modifier = Modifier.size(40.dp),
                        letterSize = 18.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (source.isPinned) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = title(source),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val subtitle = listOfNotNull(
                            parser?.contentType?.name?.let { prettify(it) },
                            parser?.locale?.let { localeDisplayName(it) } ?: stringResource(Res.string.various_languages),
                        ).joinToString(", ")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Per-source overflow (pin, reorder, settings) — Doki's popup_source_config.
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (source.isPinned) Res.string.unpin else Res.string.pin)) },
                                onClick = { viewModel.setPinned(source.mangaSource, !source.isPinned); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.move_up)) },
                                enabled = !searchActive && index > 0,
                                onClick = { viewModel.move(index, index - 1); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.move_down)) },
                                enabled = !searchActive && index < shown.lastIndex,
                                onClick = { viewModel.move(index, index + 1); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.settings)) },
                                onClick = { onSourceSettings(source.name); menu = false },
                            )
                        }
                    }
                    // Disable (remove from the active list -> back to catalog).
                    IconButton(onClick = { viewModel.setEnabled(source.mangaSource, false) }) {
                        Icon(Icons.Filled.Block, contentDescription = stringResource(Res.string.disable))
                    }
                }
            }
        }
    }
}
