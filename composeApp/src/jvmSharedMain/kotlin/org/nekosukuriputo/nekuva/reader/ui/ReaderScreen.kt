package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
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
    val isBookmarked by viewModel.isBookmarked.collectAsState()

    // Doki-style reader overlay: tap toggles the app bar + the floating menu button.
    var controlsVisible by remember { mutableStateOf(true) }
    var showMenuSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = controlsVisible) {
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
                            IconButton(onClick = { viewModel.goToChapter(-1) }, enabled = state.hasPrev) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(Res.string.prev_chapter))
                            }
                            IconButton(onClick = { viewModel.goToChapter(1) }, enabled = state.hasNext) {
                                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(Res.string.next_chapter))
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (uiState is ReaderUiState.Success) {
                AnimatedVisibility(visible = controlsVisible) {
                    FloatingActionButton(onClick = { showMenuSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(Res.string.options))
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
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
                    onVisibleIndexChanged = { viewModel.onVisibleIndexChanged(it) },
                    onToggleControls = { controlsVisible = !controlsVisible },
                )
            }
        }
    }

    if (showMenuSheet) {
        ReaderMenuSheet(
            isBookmarked = isBookmarked,
            onToggleBookmark = {
                viewModel.toggleBookmark()
                showMenuSheet = false
            },
            onDismiss = { showMenuSheet = false },
        )
    }
}

/**
 * Reader options bottom sheet — UI mirrors Doki's reader config sheet. Only the bookmark action is
 * functional this session; the rest (save page, reading modes, settings, color correction, auto-scroll,
 * etc.) are shown but disabled and deferred to the reader-polish session (see MIGRATION.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMenuSheet(
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(Res.string.options),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Deferred (reader-polish / save area)
            MenuRow(Icons.Filled.Save, stringResource(Res.string.save_page), enabled = false) {}
            // FUNCTIONAL
            MenuRow(
                icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                title = stringResource(if (isBookmarked) Res.string.bookmark_remove else Res.string.bookmark_add),
                enabled = true,
                onClick = onToggleBookmark,
            )

            Text(
                text = stringResource(Res.string.read_mode),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val modes = listOf(
                stringResource(Res.string.standard),
                stringResource(Res.string.right_to_left),
                stringResource(Res.string.vertical),
                stringResource(Res.string.webtoon),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                modes.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = index == 3, // Webtoon (current default), display-only
                        onClick = {},
                        enabled = false,
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Text(
                text = stringResource(Res.string.reader_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SwitchRow(stringResource(Res.string.use_two_pages_landscape))
            SwitchRow(stringResource(Res.string.enable_pull_gesture_title))
            MenuRow(Icons.Filled.ScreenRotation, stringResource(Res.string.rotate_screen), enabled = false) {}
            MenuRow(Icons.Filled.Timer, stringResource(Res.string.automatic_scroll), enabled = false) {}
            MenuRow(Icons.Filled.Palette, stringResource(Res.string.color_correction), enabled = false) {}
            MenuRow(Icons.Filled.Settings, stringResource(Res.string.settings), enabled = false) {}
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(0.5f).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = false, onCheckedChange = null, enabled = false)
    }
}

@Composable
fun ReaderContent(
    pages: List<LoadedPage>,
    paddingValues: PaddingValues,
    scrollToIndex: Int,
    scrollToken: Int,
    onVisibleIndexChanged: (Int) -> Unit,
    onToggleControls: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            // Tap toggles the reader controls (drag still scrolls the list).
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }) },
    ) {
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
