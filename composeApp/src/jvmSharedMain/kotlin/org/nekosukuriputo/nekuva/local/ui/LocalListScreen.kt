package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaBadges
import org.nekosukuriputo.nekuva.core.ui.components.MangaListContent
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.list.ui.rememberMangaListDecorations
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LocalListScreen(
    viewModel: LocalListViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)          // Local follows the global list mode
    val gridSize = rememberGridSize(settings)
    val showSavedBadge = (settings.getMangaListBadges() and 2) != 0 // downloaded == "saved"
    val deco = rememberMangaListDecorations() // reading progress + favourite badge (Doki indicators)
    // Multi-select (Doki mode_local): long-press enters; contextual bar select-all/share/delete.
    val selection = org.nekosukuriputo.nekuva.core.ui.selection.rememberSelectionState<Long>()
    val mangas = (uiState as? LocalListUiState.Success)?.mangaList.orEmpty()
    fun selected() = mangas.filter { selection.isSelected(it.id) }
    val sortOrder by viewModel.sortOrder.collectAsState()
    var showSortDialog by remember { mutableStateOf(false) }
    // Local import (Doki action_import): pick a .cbz → copy into library → parse (list auto-refreshes via the bus).
    val importer = koinInject<org.nekosukuriputo.nekuva.local.domain.MangaImportUseCase>()
    val filePicker = rememberMangaFilePicker()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val importDoneMsg = stringResource(Res.string.import_completed)
    val importErrMsg = stringResource(Res.string.error_occurred)

    Scaffold(
        topBar = {
            if (selection.isActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selection.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                    },
                    title = { Text(selection.count.toString()) },
                    actions = {
                        IconButton(onClick = { selection.selectAll(mangas.map { it.id }) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(Res.string.select_all))
                        }
                        IconButton(onClick = { org.nekosukuriputo.nekuva.core.share.shareMangas(selected()); selection.clear() }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.delete))
                        }
                    },
                )
            } else {
                // Thin bar with the import + sort actions (Doki opt_local); shell MainTopBar sits above.
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching {
                                    filePicker.pickCbz { name, input -> importer.import(name, input) }
                                }.onSuccess { picked ->
                                    if (picked) snackbarHostState.showSnackbar(importDoneMsg)
                                }.onFailure {
                                    snackbarHostState.showSnackbar(importErrMsg)
                                }
                            }
                        }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = stringResource(Res.string._import))
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(Res.string.sort_order))
                        }
                    },
                )
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val state = uiState) {
            is LocalListUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is LocalListUiState.Empty -> EmptyState(message = stringResource(Res.string.nothing_here), modifier = Modifier.padding(paddingValues))
            is LocalListUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.loadManga() }, modifier = Modifier.padding(paddingValues))
            is LocalListUiState.Success -> {
                MangaListContent(
                    mangas = state.mangaList,
                    listMode = listMode,
                    gridSize = gridSize,
                    modifier = Modifier.padding(paddingValues),
                    onClick = { if (selection.isActive) selection.toggle(it.id) else onMangaClick(it.id) },
                    onLongClick = { selection.toggle(it.id) },
                    progressOf = { deco.progressOf(it) },
                    badgesOf = { MangaBadges(saved = showSavedBadge, favourite = deco.badgesOf(it).favourite) },
                    selectedIds = selection.selected,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val toDelete = selected()
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text(toDelete.joinToString("\n") { it.title }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteManga(toDelete)
                    showDeleteConfirm = false
                    selection.clear()
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showSortDialog) {
        // Local uses the parser SortOrder (NEWEST/ALPHABETICAL/RATING) — Doki KEY_LOCAL_LIST_ORDER.
        val options = listOf(
            org.nekosukuriputo.nekuva.parsers.model.SortOrder.NEWEST to Res.string.newest,
            org.nekosukuriputo.nekuva.parsers.model.SortOrder.ALPHABETICAL to Res.string.by_name,
            org.nekosukuriputo.nekuva.parsers.model.SortOrder.RATING to Res.string.by_rating,
        )
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(Res.string.sort_order)) },
            text = {
                Column {
                    options.forEach { (order, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.setSortOrder(order); showSortDialog = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = order == sortOrder, onClick = {
                                viewModel.setSortOrder(order); showSortDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text(stringResource(Res.string.done)) } },
        )
    }
}
