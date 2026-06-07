package org.nekosukuriputo.nekuva.details.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel = koinViewModel(),
    onChapterClick: (Long, Long) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.details)) },
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is DetailsUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is DetailsUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(paddingValues))
            is DetailsUiState.Success -> {
                MangaDetailsContent(
                    manga = state.manga,
                    paddingValues = paddingValues,
                    onChapterClick = { chapter -> onChapterClick(state.manga.id, chapter.id) }
                )
            }
        }
    }
}

@Composable
fun MangaDetailsContent(
    manga: Manga,
    paddingValues: PaddingValues,
    onChapterClick: (MangaChapter) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = manga.largeCoverUrl ?: manga.coverUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(120.dp).aspectRatio(0.7f)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = manga.title, style = MaterialTheme.typography.titleLarge)
                    Text(text = manga.authors.joinToString().takeIf { it.isNotEmpty() } ?: "Unknown Author", style = MaterialTheme.typography.bodyMedium)
                    Text(text = manga.state?.name ?: "Unknown Status", style = MaterialTheme.typography.labelMedium)
                    if (manga.rating >= 0f) {
                        Text(text = "Rating: ${manga.rating}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        val description = manga.description?.replace("<br>".toRegex(RegexOption.IGNORE_CASE), "\n")?.replace("<[^>]*>".toRegex(), "")?.trim()
        if (!description.isNullOrEmpty()) {
            item {
                Text(text = stringResource(Res.string.description), style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        if (!manga.tags.isNullOrEmpty()) {
            item {
                Text(text = manga.tags!!.joinToString { it.title }, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Text(text = stringResource(Res.string.chapters), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        }

        items(manga.chapters ?: emptyList()) { chapter ->
            ChapterItem(chapter = chapter, onClick = { onChapterClick(chapter) })
        }
    }
    org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
        state = listState,
        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
    )
}
}

@Composable
fun ChapterItem(chapter: MangaChapter, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        val chapterTitle = chapter.title?.takeIf { it.isNotEmpty() } ?: chapter.name
        Text(text = chapterTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chapter.uploadDate > 0L) {
            Text(text = org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo(chapter.uploadDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
