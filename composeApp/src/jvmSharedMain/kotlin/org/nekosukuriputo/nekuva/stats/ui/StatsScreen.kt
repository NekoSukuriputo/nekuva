package org.nekosukuriputo.nekuva.stats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.stats.domain.StatsPeriod
import org.nekosukuriputo.nekuva.stats.domain.StatsRecord
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*

/** Reading statistics (Doki StatsActivity): period filter + per-manga reading time with proportion bars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBackClick: () -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val period by viewModel.period.collectAsState()
    val stats by viewModel.readingStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    val total = stats.sumOf { it.duration }.coerceAtLeast(1L)
    val otherLabel = stringResource(Res.string.other)

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(Res.string.statistics)) },
            text = { Text(stringResource(Res.string.clear)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStats(); showClearDialog = false }) {
                    Text(stringResource(Res.string.clear))
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Period filter (Doki period spinner).
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(StatsPeriod.entries) { p ->
                    FilterChip(
                        selected = p == period,
                        onClick = { viewModel.setPeriod(p) },
                        label = { Text(periodLabel(p)) },
                    )
                }
            }
            // Category filter (Doki category multi-select); only when categories exist.
            if (categories.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = cat.id in selectedCategories,
                            onClick = { viewModel.setCategoryChecked(cat.id, cat.id !in selectedCategories) },
                            label = { Text(cat.title) },
                        )
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                stats.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.nothing_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(stats, key = { it.manga?.id ?: -1L }) { record ->
                        StatsRow(record = record, fraction = record.duration.toFloat() / total, otherLabel = otherLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(record: StatsRecord, fraction: Float, otherLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val cover = record.manga?.coverUrl
            if (cover != null) {
                AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = record.manga?.title ?: otherLabel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(4.dp))
        Text(text = formatDuration(record.hours, record.minutes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = when (period) {
    StatsPeriod.DAY -> stringResource(Res.string.day)
    StatsPeriod.WEEK -> stringResource(Res.string.week)
    StatsPeriod.MONTH -> stringResource(Res.string.month)
    StatsPeriod.MONTHS_3 -> stringResource(Res.string.three_months)
    StatsPeriod.ALL -> stringResource(Res.string.all_time)
}

@Composable
private fun formatDuration(hours: Int, minutes: Int): String = when {
    hours == 0 && minutes == 0 -> stringResource(Res.string.less_than_minute)
    hours == 0 -> stringResource(Res.string.minutes_short, minutes)
    minutes == 0 -> stringResource(Res.string.hours_short, hours)
    else -> stringResource(Res.string.hours_minutes_short, hours, minutes)
}
