package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.download.ui.dialog.pickMangaDirectory
import org.nekosukuriputo.nekuva.download.ui.dialog.supportsDirectoryPicker
import org.nekosukuriputo.nekuva.settings.ui.network.formatBytes
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.add
import nekuva.composeapp.generated.resources.available
import nekuva.composeapp.generated.resources.directories
import nekuva.composeapp.generated.resources.download_default_directory
import nekuva.composeapp.generated.resources.private_app_directory_warning
import nekuva.composeapp.generated.resources.remove

/**
 * "Manga directories" screen (Doki MangaDirectoriesActivity): a card per storage directory with a usage
 * meter; add a custom directory (SAF on Android / file chooser on Desktop) or remove a custom one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDirectoriesScreen(
    onBackClick: () -> Unit,
    viewModel: MangaDirectoriesViewModel = koinViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.directories)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            if (supportsDirectoryPicker) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(Res.string.add)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            val path = pickMangaDirectory()
                            if (path != null) viewModel.addDirectory(path)
                        }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.path }) { dir ->
                DirectoryCard(dir, onRemove = { viewModel.removeDirectory(dir) })
            }
        }
    }
}

@Composable
private fun DirectoryCard(model: DirectoryConfigModel, onRemove: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.title, style = MaterialTheme.typography.titleMedium)
            Text(
                model.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatBytes(model.availableBytes)} ${stringResource(Res.string.available).lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val usedFraction = if (model.totalBytes > 0) {
                ((model.totalBytes - model.availableBytes).toFloat() / model.totalBytes).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(progress = { usedFraction }, modifier = Modifier.fillMaxWidth())
            val description = when {
                model.isDefault -> stringResource(Res.string.download_default_directory)
                model.isAppPrivate -> stringResource(Res.string.private_app_directory_warning)
                else -> null
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Only user-added (non-app-private) directories can be removed (Doki).
            if (!model.isAppPrivate) {
                TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(Res.string.remove))
                }
            }
        }
    }
}
