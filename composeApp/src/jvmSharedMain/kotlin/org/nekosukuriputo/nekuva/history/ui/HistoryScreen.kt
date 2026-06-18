package org.nekosukuriputo.nekuva.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ListMode
import org.nekosukuriputo.nekuva.core.prefs.ProgressIndicatorMode
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaGridItem
import org.nekosukuriputo.nekuva.core.ui.components.mangaGridCells
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo
import org.nekosukuriputo.nekuva.core.util.ext.relativeDateKey
import org.nekosukuriputo.nekuva.history.domain.model.MangaWithHistory
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onMangaClick: (Long) -> Unit,
    onResumeClick: (Long, Long) -> Unit,
    viewModel: HistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings, AppSettings.KEY_LIST_MODE_HISTORY)
    val gridSize = rememberGridSize(settings)
    val progressMode = remember { settings.progressIndicatorMode }
    // Multi-select (Doki ActionMode / mode_history): long-press enters, tap toggles while active.
    val selection = org.nekosukuriputo.nekuva.core.ui.selection.rememberSelectionState()
    val successList = (uiState as? HistoryUiState.Success)?.list.orEmpty()
    fun selectedMangas() = successList.filter { selection.isSelected(it.manga.id) }.map { it.manga }
    // Pagination (CORE-8): load the next page when scrolled near the end (VM no-ops if the last page was full).
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(gridState) {
        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 4) viewModel.loadMore() }
    }
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 4) viewModel.loadMore() }
    }

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
                        IconButton(onClick = { selection.selectAll(successList.map { it.manga.id }) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(Res.string.select_all))
                        }
                        IconButton(onClick = { org.nekosukuriputo.nekuva.core.share.shareMangas(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                        }
                        IconButton(onClick = { viewModel.markAsRead(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.DoneAll, contentDescription = stringResource(Res.string.mark_as_completed))
                        }
                        IconButton(onClick = { viewModel.removeHistory(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.remove))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(Res.string.history)) },
                    actions = {
                        IconButton(onClick = { viewModel.clearAllHistory() }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.clear_history))
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is HistoryUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is HistoryUiState.Error -> ErrorState(error = state.error, onRetry = { }, modifier = Modifier.padding(paddingValues))
            is HistoryUiState.Success -> {
                if (state.list.isEmpty()) {
                    EmptyState(message = stringResource(Res.string.text_history_holder_primary), modifier = Modifier.padding(paddingValues))
                } else {
                    // Group by Doki-style relative bucket (today/yesterday/N-days/per-day) so headers read
                    // "Hari ini"/"Kemarin"/"N hari lalu" then the absolute date ("24 Mei 2026").
                    val grouped = state.list.groupBy { relativeDateKey(it.history.updatedAt) }
                    fun progressOf(item: MangaWithHistory): Float? =
                        if (progressMode != ProgressIndicatorMode.NONE && item.history.percent >= 0f) item.history.percent else null

                    if (listMode == ListMode.GRID) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = mangaGridCells(gridSize),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                        ) {
                            grouped.forEach { (_, items) ->
                                item(span = { GridItemSpan(maxLineSpan) }) { DateHeader(items.first().history.updatedAt) }
                                gridItems(items, key = { it.manga.id }) { item ->
                                    MangaGridItem(
                                        manga = item.manga,
                                        onClick = { if (selection.isActive) selection.toggle(item.manga.id) else onMangaClick(item.manga.id) },
                                        onLongClick = { selection.toggle(item.manga.id) },
                                        progress = progressOf(item),
                                        selected = selection.isSelected(item.manga.id),
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxSize().padding(paddingValues)
                        ) {
                            grouped.forEach { (_, items) ->
                                item { DateHeader(items.first().history.updatedAt) }
                                items(items, key = { it.manga.id }) { item ->
                                    HistoryItem(
                                        item = item,
                                        showProgress = progressMode != ProgressIndicatorMode.NONE,
                                        onClick = { if (selection.isActive) selection.toggle(item.manga.id) else onMangaClick(item.manga.id) },
                                        onResumeClick = { onResumeClick(item.manga.id, item.history.chapterId) },
                                        onDeleteClick = { viewModel.removeHistory(item.manga) },
                                        onLongClick = { selection.toggle(item.manga.id) },
                                        selected = selection.isSelected(item.manga.id),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun DateHeader(epochMillis: Long) {
    Text(
        text = calculateTimeAgo(epochMillis),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    item: MangaWithHistory,
    onClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    showProgress: Boolean = true,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.manga.coverUrl,
                contentDescription = item.manga.title,
                modifier = Modifier
                    .width(60.dp)
                    .height(80.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val percentageStr = if (showProgress && item.history.percent >= 0f) {
                    val percentInt = (item.history.percent * 100).toInt()
                    " • $percentInt%"
                } else ""

                Text(
                    text = stringResource(Res.string.resume) + percentageStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.remove_from_history))
            }
            IconButton(onClick = onResumeClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(Res.string.resume))
            }
        }
    }
}
