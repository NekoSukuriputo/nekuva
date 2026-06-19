package org.nekosukuriputo.nekuva.alternatives.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaListContent
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.parsers.model.Manga
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.alternatives
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.fix
import nekuva.composeapp.generated.resources.migrate
import nekuva.composeapp.generated.resources.migrate_confirmation
import nekuva.composeapp.generated.resources.nothing_found
import nekuva.composeapp.generated.resources.search_disabled_sources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativesScreen(
    viewModel: AlternativesViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onBackClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val isMigrating by viewModel.isMigrating.collectAsState()
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)
    val gridSize = rememberGridSize(settings)
    // Long-press a result -> confirm migrating the current manga to that source (Doki migrate).
    var migrateTarget by remember { mutableStateOf<Manga?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.alternatives)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // Auto-fix: pick the best alternative and migrate to it (Doki AutoFix).
                    if (state.results.isNotEmpty() && !isMigrating) {
                        IconButton(onClick = { viewModel.autoFix { id -> onMangaClick(id) } }) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(Res.string.fix))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading || isMigrating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                state.results.isNotEmpty() -> {
                    MangaListContent(
                        mangas = state.results,
                        listMode = listMode,
                        gridSize = gridSize,
                        modifier = Modifier.weight(1f),
                        onClick = { onMangaClick(it.id) },
                        onLongClick = { if (!isMigrating) migrateTarget = it },
                    )
                }
                state.isLoading -> LoadingState(modifier = Modifier.weight(1f).fillMaxWidth())
                else -> EmptyState(
                    message = stringResource(Res.string.nothing_found),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            // Doki throughDisabledSources: widen the search to disabled sources once enabled ones settle.
            if (state.canSearchDisabled) {
                OutlinedButton(
                    onClick = { viewModel.searchDisabled() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(Res.string.search_disabled_sources))
                }
            }
        }
    }

    val target = migrateTarget
    val ref = state.refManga
    if (target != null && ref != null) {
        AlertDialog(
            onDismissRequest = { migrateTarget = null },
            title = { Text(stringResource(Res.string.migrate)) },
            text = {
                Text(
                    stringResource(
                        Res.string.migrate_confirmation,
                        ref.title, ref.source.name, target.title, target.source.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    migrateTarget = null
                    viewModel.migrateTo(target) { id -> onMangaClick(id) }
                }) { Text(stringResource(Res.string.migrate)) }
            },
            dismissButton = {
                TextButton(onClick = { migrateTarget = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
