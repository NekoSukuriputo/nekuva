package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ListMode
import org.nekosukuriputo.nekuva.parsers.model.Manga

/** Cover aspect ratio used across the app (Doki's 13:18 ≈ 0.72). */
private const val COVER_RATIO = 0.72f

/** Per-item status badges (Doki `manga_list_badges`): "1" = favourite, "2" = saved/downloaded. */
data class MangaBadges(val favourite: Boolean = false, val saved: Boolean = false, val bookmarked: Boolean = false) {
    val any: Boolean get() = favourite || saved || bookmarked
}

/** Effective list mode for a screen — per-screen override (e.g. favourites) falls back to the global one. Live. */
@Composable
fun rememberMangaListMode(settings: AppSettings, screenKey: String? = null): ListMode {
    val global = settings.observeListModeOrNull(AppSettings.KEY_LIST_MODE)
        .collectAsState(initial = settings.listMode).value
    if (screenKey == null) return global ?: ListMode.GRID
    val perScreen = settings.observeListModeOrNull(screenKey).collectAsState(initial = null).value
    return perScreen ?: global ?: ListMode.GRID
}

/** Live grid-size percent (Doki `grid_size`). */
@Composable
fun rememberGridSize(settings: AppSettings): Int =
    settings.observeGridSize().collectAsState(initial = settings.gridSize).value

/** Grid columns from the grid-size percent — base ≈ 120dp cell at 100% (Doki scales cell width). */
fun mangaGridCells(gridSize: Int): GridCells =
    GridCells.Adaptive((120 * gridSize / 100).coerceIn(80, 220).dp)

/**
 * Reusable manga list/grid container honoring the active [listMode] + [gridSize] (Doki parity). GRID =
 * adaptive grid; LIST/DETAILED_LIST = a column of rows. Screens with a plain `List<Manga>` use this;
 * screens with custom layouts (sections, load-more) call the item composables directly.
 */
@Composable
fun MangaListContent(
    mangas: List<Manga>,
    listMode: ListMode,
    gridSize: Int,
    onClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    onLongClick: (Manga) -> Unit = {},
    progressOf: (Manga) -> Float? = { null },
    badgesOf: (Manga) -> MangaBadges = { MangaBadges() },
) {
    if (listMode == ListMode.GRID) {
        LazyVerticalGrid(
            columns = mangaGridCells(gridSize),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            gridItems(mangas, key = { it.id }) { m ->
                MangaGridItem(m, { onClick(m) }, { onLongClick(m) }, progressOf(m), badgesOf(m))
            }
        }
    } else {
        val detailed = listMode == ListMode.DETAILED_LIST
        LazyColumn(contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
            items(mangas, key = { it.id }) { m ->
                MangaListRow(m, { onClick(m) }, { onLongClick(m) }, detailed, progressOf(m), badgesOf(m))
            }
        }
    }
}

/** Grid card (Doki `item_manga_grid`): cover + optional progress bar + badges, title below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaGridItem(
    manga: Manga,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    progress: Float? = null,
    badges: MangaBadges = MangaBadges(),
    selected: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(COVER_RATIO)) {
                CoverImage(manga.coverUrl, manga.title, Modifier.fillMaxSize())
                if (badges.any) BadgeRow(badges, Modifier.align(Alignment.TopEnd).padding(4.dp))
                progress?.let {
                    LinearProgressIndicator(
                        progress = { it.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    )
                }
                // Selection overlay (Doki ActionMode): primary scrim + check when selected.
                if (selected) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(50)),
                    )
                }
            }
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Row item for LIST / DETAILED_LIST (Doki `item_manga_list` / `item_manga_list_details`). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaListRow(
    manga: Manga,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    detailed: Boolean = false,
    progress: Float? = null,
    badges: MangaBadges = MangaBadges(),
) {
    val coverWidth = if (detailed) 64.dp else 44.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(coverWidth).aspectRatio(COVER_RATIO).clip(RoundedCornerShape(6.dp))) {
            CoverImage(manga.coverUrl, manga.title, Modifier.fillMaxSize())
            if (detailed) progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (detailed) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (detailed) {
                manga.author ?: manga.tags.take(3).joinToString { it.title }.ifEmpty { null }
            } else {
                manga.author ?: manga.tags.firstOrNull()?.title
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (detailed) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (detailed) {
                val tags = manga.tags.take(3).joinToString { it.title }
                if (tags.isNotEmpty() && manga.author != null) {
                    Text(
                        text = tags,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (badges.any) {
            Spacer(Modifier.width(8.dp))
            BadgeRow(badges, Modifier)
        }
    }
}

@Composable
private fun CoverImage(url: String?, title: String, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        },
    )
}

@Composable
private fun BadgeRow(badges: MangaBadges, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badges.favourite) Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(14.dp))
        if (badges.saved) Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(14.dp))
        if (badges.bookmarked) Icon(Icons.Filled.Bookmark, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
