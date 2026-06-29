package org.nekosukuriputo.nekuva.tracker.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.image.mangaSourceExtra
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo
import org.nekosukuriputo.nekuva.core.util.ext.relativeDateKey
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.tracker.domain.model.FeedLogItem
import org.nekosukuriputo.nekuva.tracker.domain.model.MangaTracking
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.more
import nekuva.composeapp.generated.resources.updates
import nekuva.composeapp.generated.resources.new_chapters
import nekuva.composeapp.generated.resources.text_empty_holder_primary
import nekuva.composeapp.generated.resources.text_feed_holder

/**
 * Feed/Updates tab (Doki FeedFragment parity). The toolbar (search + overflow: Update / Show updated /
 * Clear feed) lives in the shell. Content = an optional "Updated manga" header row (Doki show_updated) +
 * the date-grouped tracking log; tapping an entry marks it read and opens details.
 */
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onSeeAllUpdates: () -> Unit = {},
) {
    val logItems by viewModel.logItems.collectAsState()
    val updatedManga by viewModel.updatedManga.collectAsState()
    val headerEnabled by viewModel.isHeaderEnabled.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val quickFilters by viewModel.quickFilterOptions.collectAsState()
    val appliedFilter by viewModel.appliedFilter.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Manual "Update" (Doki action_update) runs via the shell overflow; show its progress here.
        if (isRefreshing) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        // Quick-filter chips (Doki UpdatesListQuickFilter): stay visible even on empty so the user can clear.
        if (!isLoading && quickFilters.isNotEmpty()) {
            QuickFilterRow(quickFilters, appliedFilter) { viewModel.toggleFilter(it) }
        }
    when {
        isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
        logItems.isEmpty() && (!headerEnabled || updatedManga.isEmpty()) -> FeedEmptyState()
        else -> {
            val listState = rememberLazyListState()
            // Pagination (Doki requestMoreItems): load more as the end of the log approaches.
            val loadMore by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= listState.layoutInfo.totalItemsCount - 3
                }
            }
            LaunchedEffect(loadMore) { if (loadMore) viewModel.requestMoreItems() }

            // Group the (createdAt-DESC) log by relative day; groups stay newest-first.
            val grouped = remember(logItems) { logItems.groupBy { relativeDateKey(it.createdAt) } }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (headerEnabled && updatedManga.isNotEmpty()) {
                    item(key = "updated_header") { UpdatedMangaHeader(updatedManga, onMangaClick, onSeeAllUpdates) }
                }
                grouped.forEach { (_, items) ->
                    item(key = "date_${items.first().id}") { DateHeader(items.first().createdAt) }
                    items(items, key = { it.id }) { item ->
                        FeedLogRow(
                            item = item,
                            onClick = {
                                viewModel.onItemClick(item.id)
                                onMangaClick(item.manga.id)
                            },
                        )
                    }
                }
            }
        }
    }
    }
}

/** Quick-filter chip row (Doki QuickFilterAD): tap a favourite-category chip to filter the feed. */
@Composable
private fun QuickFilterRow(
    options: List<org.nekosukuriputo.nekuva.list.domain.ListFilterOption>,
    applied: Set<org.nekosukuriputo.nekuva.list.domain.ListFilterOption>,
    onToggle: (org.nekosukuriputo.nekuva.list.domain.ListFilterOption) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.groupKey + (it.titleText ?: "") }) { option ->
            androidx.compose.material3.FilterChip(
                selected = option in applied,
                onClick = { onToggle(option) },
                label = { Text(option.titleText?.toString().orEmpty(), maxLines = 1) },
            )
        }
    }
}

/** Doki's "Updated manga" header: a horizontal row of recently-updated covers (toggle: show_updated),
 *  with a "See all" link to the full Updates grid (Doki UpdatesActivity). */
@Composable
private fun UpdatedMangaHeader(items: List<MangaTracking>, onMangaClick: (Long) -> Unit, onSeeAll: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.updates), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onSeeAll) { Text(stringResource(Res.string.more)) }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.manga.id }) { tracking ->
            Column(
                modifier = Modifier.width(88.dp).clickable { onMangaClick(tracking.manga.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    MangaCover(tracking.manga, Modifier.fillMaxWidth().aspectRatio(13f / 18f).clip(RoundedCornerShape(6.dp)))
                    if (tracking.newChapters > 0) {
                        Badge(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) { Text("+${tracking.newChapters}") }
                    }
                }
                Text(
                    text = tracking.manga.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val authorText = tracking.manga.authors.joinToString(", ").takeIf { it.isNotEmpty() }
                if (authorText != null) {
                    Text(
                        text = authorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
  }
}

/** Relative-day group header (Doki ListHeader): "Today" / "Yesterday" / date. */
@Composable
private fun DateHeader(createdAt: Long) {
    Text(
        text = calculateTimeAgo(createdAt),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** One log entry (Doki item_feed): cover + title + new-chapter names; an unread dot for "new". */
@Composable
private fun FeedLogRow(item: FeedLogItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover(item.manga, Modifier.width(48.dp).aspectRatio(13f / 18f).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.manga.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = item.chapters.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: "+${item.count} ${stringResource(Res.string.new_chapters)}"
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isUnread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun MangaCover(manga: Manga, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val model = remember(manga.coverUrl, manga.source) {
        ImageRequest.Builder(context)
            .data(manga.coverUrl)
            .apply { mangaSourceExtra(manga.source) }
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Empty state (Doki ic_empty_feed + text_empty_holder_primary + text_feed_holder). */
@Composable
private fun FeedEmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Filled.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(Res.string.text_empty_holder_primary),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.text_feed_holder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
