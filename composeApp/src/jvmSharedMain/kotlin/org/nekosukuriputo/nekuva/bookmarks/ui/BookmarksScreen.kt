package org.nekosukuriputo.nekuva.bookmarks.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.image.mangaSourceExtra
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long, page: Int) -> Unit,
    onBackClick: () -> Unit,
) {
    val state by viewModel.content.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val removedMsg = stringResource(Res.string.bookmarks_removed)
    val undoLabel = stringResource(Res.string.undo)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.onRemoved.collect {
            val result = snackbarHostState.showSnackbar(message = removedMsg, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
        }
    }

    val pageSavedMsg = stringResource(Res.string.page_saved)
    val pagesSavedMsg = stringResource(Res.string.pages_saved)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.onPagesSaved.collect { count ->
            if (count > 0) snackbarHostState.showSnackbar(if (count == 1) pageSavedMsg else pagesSavedMsg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selection.isNotEmpty()) selection.size.toString() else stringResource(Res.string.bookmarks),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selection.isNotEmpty()) viewModel.clearSelection() else onBackClick() }) {
                        Icon(
                            if (selection.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (selection.isNotEmpty()) {
                        // Save selected page images to storage (Doki bookmarks "save" selection action).
                        IconButton(onClick = { viewModel.saveSelected() }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(Res.string.save))
                        }
                        IconButton(onClick = { viewModel.removeSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (val s = state) {
            is BookmarksUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is BookmarksUiState.Error -> ErrorState(error = s.error, onRetry = { }, modifier = Modifier.padding(paddingValues))
            is BookmarksUiState.Empty -> {
                EmptyState(
                    message = stringResource(Res.string.no_bookmarks_yet),
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
            }
            is BookmarksUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.groups.forEach { (manga, bookmarks) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h:${manga.id}") {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMangaClick(manga.id) }
                                    .padding(vertical = 8.dp),
                            )
                        }
                        items(bookmarks, key = { it.pageId }) { bm ->
                            BookmarkThumb(
                                bookmark = bm,
                                selected = bm.pageId in selection,
                                onClick = {
                                    if (selection.isNotEmpty()) {
                                        viewModel.toggleSelection(bm.pageId)
                                    } else {
                                        onOpenReader(bm.manga.id, bm.chapterId, bm.page)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(bm.pageId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkThumb(
    bookmark: Bookmark,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(13f / 18f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // Thumbnail = the actual bookmarked page image (loaded from its URL, like Doki). Carry the source
        // so the network layer adds its Referer/UA + CloudFlare handling (else protected thumbs are blank).
        val thumbCtx = coil3.compose.LocalPlatformContext.current
        val thumbModel = remember(bookmark.imageUrl, bookmark.manga.source) {
            coil3.request.ImageRequest.Builder(thumbCtx)
                .data(bookmark.imageUrl)
                .apply { mangaSourceExtra(bookmark.manga.source) }
                .build()
        }
        AsyncImage(
            model = thumbModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (bookmark.percent in 0f..1f) {
            LinearProgressIndicator(
                progress = { bookmark.percent },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}
