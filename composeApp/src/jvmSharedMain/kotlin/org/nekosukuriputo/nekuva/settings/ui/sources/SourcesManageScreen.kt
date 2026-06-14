package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

private fun title(source: org.nekosukuriputo.nekuva.core.model.MangaSourceInfo): String =
    (source.mangaSource as? MangaParserSource)?.title ?: source.name

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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title(source),
                        modifier = Modifier.weight(1f)
                            .clickable { onSourceSettings(source.name) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { viewModel.setPinned(source.mangaSource, !source.isPinned) }) {
                        Icon(
                            if (source.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(if (source.isPinned) Res.string.unpin else Res.string.pin),
                            tint = if (source.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Reorder is only meaningful without an active search filter.
                    IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = !searchActive && index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                    }
                    IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = !searchActive && index < shown.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    Switch(checked = true, onCheckedChange = { viewModel.setEnabled(source.mangaSource, false) })
                }
            }
        }
    }
}
