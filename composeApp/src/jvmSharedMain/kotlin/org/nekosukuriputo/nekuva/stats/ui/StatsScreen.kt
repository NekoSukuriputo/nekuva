package org.nekosukuriputo.nekuva.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.stats.domain.StatsPeriod
import org.nekosukuriputo.nekuva.stats.domain.StatsRecord
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*

/**
 * Reading statistics (Doki StatsActivity): a period-dropdown chip + favourite-category filter chips, a
 * donut chart of reading-time proportions per manga, and a colour-keyed legend list below.
 */
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
    val otherLabel = stringResource(Res.string.other_manga)
    // Tap a legend row -> per-manga reading stats (Doki MangaStatsSheet).
    var statsForManga by remember { mutableStateOf<org.nekosukuriputo.nekuva.parsers.model.Manga?>(null) }
    statsForManga?.let { manga ->
        MangaStatsDialog(manga = manga, onDismiss = { statsForManga = null })
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(Res.string.clear_stats)) },
            text = { Text(stringResource(Res.string.clear_stats_confirm)) },
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
                title = { Text(stringResource(Res.string.reading_stats)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.clear_stats))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Period dropdown chip + category filter chips (Doki chip_period + category chips).
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { PeriodChip(period = period, onSelect = { viewModel.setPeriod(it) }) }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = cat.id in selectedCategories,
                        onClick = { viewModel.setCategoryChecked(cat.id, cat.id !in selectedCategories) },
                        label = { Text(cat.title) },
                    )
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                stats.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.empty_stats_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Donut chart (Doki PieChartView): segments sized by reading-time share, coloured per manga.
                    // Padding lives on the wrapper Box so it never distorts the square (circular) canvas.
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PieChart(
                                segments = stats.map { (it.duration.toFloat() / total) to statsColor(it.manga) },
                                modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f),
                            )
                        }
                    }
                    // Legend (Doki item_stats): colour swatch + title + duration. Tap → per-manga stats.
                    items(stats, key = { it.manga?.id ?: -1L }) { record ->
                        StatsLegendRow(
                            record = record,
                            otherLabel = otherLabel,
                            onClick = record.manga?.let { m -> { statsForManga = m } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodChip(period: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(periodLabel(period)) },
            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatsPeriod.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(periodLabel(p)) },
                    onClick = { onSelect(p); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun PieChart(segments: List<Pair<Float, Color>>, modifier: Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val thickness = size.minDimension * 0.22f
        val inset = thickness / 2f
        val arcSize = Size(size.width - thickness, size.height - thickness)
        val topLeft = Offset(inset, inset)
        if (segments.isEmpty()) {
            drawArc(empty, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(thickness))
            return@Canvas
        }
        var start = -90f
        segments.forEach { (fraction, color) ->
            val sweep = fraction.coerceIn(0f, 1f) * 360f
            drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(thickness))
            start += sweep
        }
    }
}

@Composable
private fun StatsLegendRow(record: StatsRecord, otherLabel: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = (if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(statsColor(record.manga)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.manga?.title ?: otherLabel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDuration(record.hours, record.minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Per-manga reading stats (Doki MangaStatsSheet): total read time + pages for the tapped manga. */
@Composable
private fun MangaStatsDialog(manga: Manga, onDismiss: () -> Unit) {
    val statsRepository = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.stats.data.StatsRepository>()
    var info by remember(manga.id) { mutableStateOf<org.nekosukuriputo.nekuva.stats.domain.MangaStatsInfo?>(null) }
    androidx.compose.runtime.LaunchedEffect(manga.id) {
        info = runCatching { statsRepository.getMangaStats(manga.id) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(manga.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val data = info
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val totalMin = ((data?.totalDurationMs ?: 0L) / 60_000L).toInt()
                Text(stringResource(Res.string.reading_time) + ": " + formatDuration(totalMin / 60, totalMin % 60))
                Text(stringResource(Res.string.pages) + ": " + (data?.totalPages ?: 0))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

/** Deterministic per-manga colour for chart segments + legend (Doki KotatsuColors.ofManga). */
private fun statsColor(manga: Manga?): Color {
    if (manga == null) return Color(0xFF9E9E9E)
    val hash = (manga.id xor (manga.id ushr 32)).toInt() and 0x7FFFFFFF
    return Color.hsv((hash % 360).toFloat(), 0.55f, 0.85f)
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
