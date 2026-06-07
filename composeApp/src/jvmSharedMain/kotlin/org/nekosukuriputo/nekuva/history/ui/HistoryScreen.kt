package org.nekosukuriputo.nekuva.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.util.ext.formatEpochToDateString
import org.nekosukuriputo.nekuva.history.domain.model.MangaWithHistory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onMangaClick: (Long) -> Unit,
    onResumeClick: (Long, Long) -> Unit,
    viewModel: HistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Baca") },
                actions = {
                    IconButton(onClick = { viewModel.clearAllHistory() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Hapus Riwayat")
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
                    EmptyState(message = "Tidak ada riwayat baca.", modifier = Modifier.padding(paddingValues))
                } else {
                    val grouped = state.list.groupBy { formatEpochToDateString(it.history.updatedAt) }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize().padding(paddingValues)
                    ) {
                        grouped.forEach { (dateStr, items) ->
                            item {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(items) { item ->
                                HistoryItem(
                                    item = item,
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

@Composable
fun HistoryItem(
    item: MangaWithHistory,
    onClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
                val percentageStr = if (item.history.percent >= 0f) {
                    val percentInt = (item.history.percent * 100).toInt()
                    " • $percentInt%"
                } else ""
                
                Text(
                    text = "Lanjut bab${percentageStr}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
            IconButton(onClick = onResumeClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
            }
        }
    }
}
