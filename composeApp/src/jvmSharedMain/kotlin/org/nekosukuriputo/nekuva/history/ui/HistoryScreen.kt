package org.nekosukuriputo.nekuva.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import org.nekosukuriputo.nekuva.core.util.ext.formatEpochToDateString
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
    // Long-press target in grid mode -> a small Resume/Remove menu (Doki context menu parity).
    var menuItem by remember { mutableStateOf<MangaWithHistory?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.history)) },
                actions = {
                    IconButton(onClick = { viewModel.clearAllHistory() }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.clear_history))
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is HistoryUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is HistoryUiState.Error -> ErrorState(error = state.error, onRetry = { }, modifier = Modifier.padding(paddingValues))
            is HistoryUiState.Success -> {
                if (state.list.isEmpty()) {
                    EmptyState(message = stringResource(Res.string.text_history_holder_primary), modifier = Modifier.padding(paddingValues))
                } else {
                    val grouped = state.list.groupBy { formatEpochToDateString(it.history.updatedAt) }
                    fun progressOf(item: MangaWithHistory): Float? =
                        if (progressMode != ProgressIndicatorMode.NONE && item.history.percent >= 0f) item.history.percent else null

                    if (listMode == ListMode.GRID) {
                        LazyVerticalGrid(
                            columns = mangaGridCells(gridSize),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                        ) {
                            grouped.forEach { (dateStr, items) ->
                                item(span = { GridItemSpan(maxLineSpan) }) { DateHeader(dateStr) }
                                gridItems(items, key = { it.manga.id }) { item ->
                                    MangaGridItem(
                                        manga = item.manga,
                                        onClick = { onMangaClick(item.manga.id) },
                                        onLongClick = { menuItem = item },
                                        progress = progressOf(item),
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxSize().padding(paddingValues)
                        ) {
                            grouped.forEach { (dateStr, items) ->
                                item { DateHeader(dateStr) }
                                items(items, key = { it.manga.id }) { item ->
                                    HistoryItem(
                                        item = item,
                                        showProgress = progressMode != ProgressIndicatorMode.NONE,
                                        onClick = { onMangaClick(item.manga.id) },
                                        onResumeClick = { onResumeClick(item.manga.id, item.history.chapterId) },
                                        onDeleteClick = { viewModel.removeHistory(item.manga) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    menuItem?.let { mi ->
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = { Text(mi.manga.title, maxLines = 2) },
            text = {},
            confirmButton = {
                TextButton(onClick = {
                    onResumeClick(mi.manga.id, mi.history.chapterId)
                    menuItem = null
                }) { Text(stringResource(Res.string.resume)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.removeHistory(mi.manga)
                    menuItem = null
                }) { Text(stringResource(Res.string.remove_from_history)) }
            },
        )
    }
}

@Composable
private fun DateHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun HistoryItem(
    item: MangaWithHistory,
    onClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    showProgress: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
