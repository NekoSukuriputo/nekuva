package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.i18n.localeDisplayName
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState

private fun prettify(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Add-new-source catalog (Doki SourcesCatalogActivity): search + locale/type/new filters + add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesCatalogScreen(
    onBackClick: () -> Unit,
    viewModel: SourcesCatalogViewModel = koinViewModel(),
) {
    val sources by viewModel.content.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var localeMenu by remember { mutableStateOf(false) }
    val allLanguages = stringResource(Res.string.all_languages)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        TextField(
                            value = query,
                            onValueChange = { query = it; viewModel.performSearch(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(Res.string.search)) },
                        )
                    } else {
                        Text(stringResource(Res.string.sources_catalog))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) { searchActive = false; query = ""; viewModel.performSearch(null) } else onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter row: language dropdown + content-type chips + "new only".
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    FilterChip(
                        selected = filter.locale != null,
                        onClick = { localeMenu = true },
                        label = { Text(filter.locale?.let { localeDisplayName(it) } ?: allLanguages, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = localeMenu, onDismissRequest = { localeMenu = false }) {
                        viewModel.locales.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text(loc?.let { localeDisplayName(it) } ?: allLanguages) },
                                onClick = { viewModel.setLocale(loc); localeMenu = false },
                            )
                        }
                    }
                }
                FilterChip(
                    selected = filter.isNewOnly,
                    onClick = { viewModel.setNewOnly(!filter.isNewOnly) },
                    label = { Text(stringResource(Res.string.new_sources), maxLines = 1) },
                )
                viewModel.contentTypes.forEach { type ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = { viewModel.setContentType(type, type !in filter.types) },
                        label = { Text(prettify(type.name), maxLines = 1) },
                    )
                }
            }

            if (sources.isEmpty()) {
                EmptyState(message = stringResource(Res.string.nothing_found), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sources, key = { it.name }) { source ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            org.nekosukuriputo.nekuva.core.ui.components.SourceFaviconImage(
                                sourceName = source.name,
                                displayName = source.title,
                                modifier = Modifier.size(40.dp),
                                letterSize = 18.sp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOfNotNull(
                                    source.locale?.let { localeDisplayName(it) },
                                    prettify(source.contentType.name),
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.addSource(source) }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.add))
                            }
                        }
                    }
                }
            }
        }
    }
}
