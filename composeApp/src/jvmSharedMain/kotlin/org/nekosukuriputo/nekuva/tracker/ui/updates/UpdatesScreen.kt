package org.nekosukuriputo.nekuva.tracker.ui.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import org.nekosukuriputo.nekuva.list.ui.rememberMangaListDecorations
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.add_to_favourites
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.default_category
import nekuva.composeapp.generated.resources.nothing_here
import nekuva.composeapp.generated.resources.save
import nekuva.composeapp.generated.resources.select_all
import nekuva.composeapp.generated.resources.share
import nekuva.composeapp.generated.resources.updates

/**
 * Dedicated "Updates" screen (Doki UpdatesFragment): a grid of every manga with unread new chapters, with
 * long-press multi-select (Doki mode_updates: Share / Add to favourites / Download). Reached from the Feed
 * tab's "Updated manga" header ("See all").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    viewModel: UpdatesViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onBackClick: () -> Unit,
) {
    val mangas by viewModel.updatedManga.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)
    val gridSize = rememberGridSize(settings)
    val deco = rememberMangaListDecorations()
    val selection = org.nekosukuriputo.nekuva.core.ui.selection.rememberSelectionState<Long>()
    var showFavDialog by remember { mutableStateOf(false) }
    fun selectedMangas() = mangas.filter { selection.isSelected(it.id) }

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
                        IconButton(onClick = { org.nekosukuriputo.nekuva.core.share.shareMangas(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                        }
                        IconButton(onClick = { showFavDialog = true }) {
                            Icon(Icons.Filled.FavoriteBorder, contentDescription = stringResource(Res.string.add_to_favourites))
                        }
                        IconButton(onClick = { viewModel.downloadManga(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(Res.string.save))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(Res.string.updates)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            isLoading -> LoadingState(modifier = Modifier.padding(padding))
            mangas.isEmpty() -> EmptyState(message = stringResource(Res.string.nothing_here), modifier = Modifier.padding(padding))
            else -> MangaListContent(
                mangas = mangas,
                listMode = listMode,
                gridSize = gridSize,
                modifier = Modifier.padding(padding),
                onClick = { if (selection.isActive) selection.toggle(it.id) else onMangaClick(it.id) },
                onLongClick = { selection.toggle(it.id) },
                progressOf = { deco.progressOf(it) },
                badgesOf = { deco.badgesOf(it) },
                selectedIds = selection.selected,
            )
        }
    }

    if (showFavDialog) {
        val categories by viewModel.favouriteCategories.collectAsState()
        val toAdd = selectedMangas()
        AlertDialog(
            onDismissRequest = { showFavDialog = false },
            title = { Text(stringResource(Res.string.add_to_favourites)) },
            text = {
                Column {
                    val rows = buildList {
                        add(0L to stringResource(Res.string.default_category))
                        categories.forEach { add(it.id to it.title) }
                    }
                    rows.forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.addToFavourites(id, toAdd); showFavDialog = false; selection.clear()
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFavDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
