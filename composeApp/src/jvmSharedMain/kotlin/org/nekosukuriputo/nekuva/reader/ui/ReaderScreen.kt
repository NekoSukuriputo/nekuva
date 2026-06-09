package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val state = uiState
                    if (state is ReaderUiState.Success) {
                        Column {
                            Text(
                                text = state.currentChapterName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.chaptersTotal > 0 && state.currentChapterIndex >= 0) {
                                Text(
                                    text = "${state.currentChapterIndex + 1}/${state.chaptersTotal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(stringResource(Res.string.loading))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val state = uiState
                    if (state is ReaderUiState.Success) {
                        IconButton(
                            onClick = { viewModel.goToChapter(-1) },
                            enabled = state.hasPrev,
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(Res.string.prev_chapter),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.goToChapter(1) },
                            enabled = state.hasNext,
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = stringResource(Res.string.next_chapter),
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ReaderUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Success -> {
                ReaderContent(
                    pages = state.pages,
                    paddingValues = paddingValues,
                    scrollToIndex = state.scrollToIndex,
                    scrollToken = state.scrollToken,
                    onVisibleIndexChanged = { viewModel.onVisibleIndexChanged(it) }
                )
            }
        }
    }
}

@Composable
fun ReaderContent(
    pages: List<LoadedPage>,
    paddingValues: PaddingValues,
    scrollToIndex: Int,
    scrollToken: Int,
    onVisibleIndexChanged: (Int) -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Resume / chapter-jump: scroll only when scrollToken changes (initial load + Next/Prev jumps),
    // NOT on every recomposition or when the continuous list grows by appending the next chapter.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToken) {
        if (pages.isNotEmpty()) {
            listState.scrollToItem(scrollToIndex.coerceIn(0, pages.lastIndex))
        }
        ready = true
    }
    // Report the visible page only after the initial scroll settled, so the transient index 0
    // emitted during the first layout pass can't overwrite the saved position with page 0.
    LaunchedEffect(ready) {
        if (ready) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { onVisibleIndexChanged(it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
        ) {
            itemsIndexed(
                items = pages,
                key = { _, p -> "${p.chapterId}_${p.pageInChapter}" },
            ) { index, loadedPage ->
                ReaderPageItem(page = loadedPage.page, index = index)
            }
        }
        org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
            state = listState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        )
    }
}

@Composable
fun ReaderPageItem(page: MangaPage, index: Int) {
    var retryHash by remember { mutableIntStateOf(0) }

    key(retryHash) {
        SubcomposeAsyncImage(
            model = page.url,
            contentDescription = "Page $index",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { retryHash++ }) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }
        )
    }
}
