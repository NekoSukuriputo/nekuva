package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState

/** Manage enabled sources (Doki SourcesManageFragment): pin, reorder (up/down), disable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesManageScreen(
    onBackClick: () -> Unit,
    viewModel: SourcesManageViewModel = koinViewModel(),
) {
    val sources by viewModel.sources.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.manage_sources)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (sources.isEmpty()) {
            EmptyState(message = stringResource(Res.string.no_manga_sources), modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            itemsIndexed(sources, key = { _, s -> s.name }) { index, source ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (source.mangaSource as? org.nekosukuriputo.nekuva.parsers.model.MangaParserSource)?.title ?: source.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Pin
                    IconButton(onClick = { viewModel.setPinned(source.mangaSource, !source.isPinned) }) {
                        Icon(
                            if (source.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(if (source.isPinned) Res.string.unpin else Res.string.pin),
                            tint = if (source.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Reorder
                    IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                    }
                    IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = index < sources.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    // Enabled toggle (off -> moves the source back to the catalog)
                    Switch(checked = true, onCheckedChange = { viewModel.setEnabled(source.mangaSource, false) })
                }
            }
        }
    }
}
