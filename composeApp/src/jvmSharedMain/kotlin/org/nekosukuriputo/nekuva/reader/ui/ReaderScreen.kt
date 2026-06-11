package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import coil3.compose.AsyncImage
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
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
    val readerMode by viewModel.readerMode.collectAsState()
    val pageIndicator by viewModel.pageIndicator.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    // Applied reader settings (R2)
    val settings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
    val readerBackground = remember { settings.readerBackground }
    val showInfoBar = remember { settings.prefBoolean("reader_bar", true) }
    val showPageNumbers = remember { settings.prefBoolean("pages_numbers", false) }

    // Doki-style reader overlay: tap toggles the app bar + the floating button.
    var controlsVisible by remember { mutableStateOf(true) }
    var showConfigSheet by remember { mutableStateOf(false) } // reader options (3-dot / long-press)
    var showChaptersSheet by remember { mutableStateOf(false) } // chapter list (bottom button)

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
                        if (uiState is ReaderUiState.Success) {
                            IconButton(onClick = { showConfigSheet = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.options))
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            val state = uiState
            if (state is ReaderUiState.Success) {
                AnimatedVisibility(visible = controlsVisible) {
                    // Wide pill (Doki-style) — larger tap target; opens the chapter list.
                    ExtendedFloatingActionButton(
                        onClick = { showChaptersSheet = true },
                        icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                        text = {
                            Text(
                                state.currentChapterName.ifEmpty { stringResource(Res.string.chapters) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { paddingValues ->
        when (val state = uiState) {
            is ReaderUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize().background(readerBackgroundColor(readerBackground))) {
                    ReaderContent(
                        pages = state.pages,
                        mode = readerMode,
                        paddingValues = paddingValues,
                        scrollToIndex = state.scrollToIndex,
                        scrollToken = state.scrollToken,
                        onVisibleIndexChanged = { viewModel.onVisibleIndexChanged(it) },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onLongPress = { showConfigSheet = true },
                    )
                    if (showInfoBar && pageIndicator.total > 0) {
                        ReaderInfoBar(
                            indicator = pageIndicator,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(paddingValues),
                        )
                    } else if (showPageNumbers && pageIndicator.total > 0) {
                        Text(
                            text = "${pageIndicator.page} / ${pageIndicator.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(paddingValues)
                                .padding(bottom = 8.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showConfigSheet) {
        ReaderConfigSheet(
            isBookmarked = isBookmarked,
            mode = readerMode,
            onSelectMode = { viewModel.setReaderMode(it) },
            onToggleBookmark = {
                viewModel.toggleBookmark()
                showConfigSheet = false
            },
            onDismiss = { showConfigSheet = false },
        )
    }
    if (showChaptersSheet) {
        val state = uiState
        if (state is ReaderUiState.Success) {
            ReaderChaptersSheet(
                chapters = state.chapters,
                pages = state.pages,
                bookmarks = bookmarks,
                onChapterClick = { id -> viewModel.goToChapterById(id); showChaptersSheet = false },
                onPageClick = { index -> viewModel.jumpToPageIndex(index); showChaptersSheet = false },
                onBookmarkClick = { chapterId, page -> viewModel.goToChapterAtPage(chapterId, page); showChaptersSheet = false },
                onDismiss = { showChaptersSheet = false },
            )
        }
    }
}

/**
 * Reader options bottom sheet — UI mirrors Doki's reader config sheet. Only the bookmark action is
 * functional this session; the rest (save page, reading modes, settings, color correction, auto-scroll,
 * etc.) are shown but disabled and deferred to the reader-polish session (see MIGRATION.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderConfigSheet(
    isBookmarked: Boolean,
    mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode,
    onSelectMode: (org.nekosukuriputo.nekuva.core.prefs.ReaderMode) -> Unit,
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
            val modeOptions = listOf(
                stringResource(Res.string.standard) to org.nekosukuriputo.nekuva.core.prefs.ReaderMode.STANDARD,
                stringResource(Res.string.right_to_left) to org.nekosukuriputo.nekuva.core.prefs.ReaderMode.REVERSED,
                stringResource(Res.string.vertical) to org.nekosukuriputo.nekuva.core.prefs.ReaderMode.VERTICAL,
                stringResource(Res.string.webtoon) to org.nekosukuriputo.nekuva.core.prefs.ReaderMode.WEBTOON,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                modeOptions.forEachIndexed { index, (label, value) ->
                    SegmentedButton(
                        selected = value == mode,
                        onClick = { onSelectMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, modeOptions.size),
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
    mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode,
    paddingValues: PaddingValues,
    scrollToIndex: Int,
    scrollToken: Int,
    onVisibleIndexChanged: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        if (mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.WEBTOON) {
            WebtoonReader(pages, scrollToIndex, scrollToken, onVisibleIndexChanged, onToggleControls, onLongPress)
        } else {
            PagedReader(pages, mode, scrollToIndex, scrollToken, onVisibleIndexChanged, onToggleControls, onLongPress)
        }
    }
}

/** Doki-style reader chapter sheet: tabs for the chapter list, page thumbnails, and bookmarks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderChaptersSheet(
    chapters: List<ReaderChapterItem>,
    pages: List<LoadedPage>,
    bookmarks: List<Bookmark>,
    onChapterClick: (Long) -> Unit,
    onPageClick: (Int) -> Unit,
    onBookmarkClick: (Long, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) } // 0=list, 1=grid, 2=bookmarks
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { tab = 0 }) {
                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(Res.string.chapters), tint = if (tab == 0) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                IconButton(onClick = { tab = 1 }) {
                    Icon(Icons.Filled.GridView, contentDescription = stringResource(Res.string.pages), tint = if (tab == 1) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                IconButton(onClick = { tab = 2 }) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = stringResource(Res.string.bookmarks), tint = if (tab == 2) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
            }
            HorizontalDivider()
            when (tab) {
                0 -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                        initialFirstVisibleItemIndex = chapters.indexOfFirst { it.isCurrent }.coerceAtLeast(0),
                    )
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                        items(chapters, key = { it.id }) { ch ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onChapterClick(ch.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (ch.isCurrent) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ch.name.ifEmpty { "Chapter ${ch.number}" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (ch.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("#${ch.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                1 -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(90.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItemsIndexed(pages, key = { _, p -> "${p.chapterId}_${p.pageInChapter}" }) { index, lp ->
                        ThumbBox(model = lp.page.preview?.takeIf { it.isNotEmpty() } ?: lp.page.url, badge = "${lp.pageInChapter + 1}") { onPageClick(index) }
                    }
                }
                2 -> if (bookmarks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.no_bookmarks_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(90.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(bookmarks, key = { it.pageId }) { bm ->
                            ThumbBox(model = bm.imageUrl, badge = null) { onBookmarkClick(bm.chapterId, bm.page) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbBox(model: String?, badge: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(13f / 18f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (badge != null) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Reader background colour from the setting (DEFAULT follows the app theme). */
@Composable
private fun readerBackgroundColor(bg: org.nekosukuriputo.nekuva.core.prefs.ReaderBackground): Color = when (bg) {
    org.nekosukuriputo.nekuva.core.prefs.ReaderBackground.DEFAULT -> MaterialTheme.colorScheme.background
    org.nekosukuriputo.nekuva.core.prefs.ReaderBackground.LIGHT -> Color(0xFFF5F5F5)
    org.nekosukuriputo.nekuva.core.prefs.ReaderBackground.DARK -> Color(0xFF202020)
    org.nekosukuriputo.nekuva.core.prefs.ReaderBackground.WHITE -> Color.White
    org.nekosukuriputo.nekuva.core.prefs.ReaderBackground.BLACK -> Color.Black
}

/** Thin bottom info bar: chapter name + page x/total. */
@Composable
private fun ReaderInfoBar(indicator: PageIndicator, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = indicator.chapterName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${indicator.page} / ${indicator.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Continuous vertical reader (full-width images, multi-chapter append). */
@Composable
private fun WebtoonReader(
    pages: List<LoadedPage>,
    scrollToIndex: Int,
    scrollToken: Int,
    onVisibleIndexChanged: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToken) {
        if (pages.isNotEmpty()) listState.scrollToItem(scrollToIndex.coerceIn(0, pages.lastIndex))
        ready = true
    }
    LaunchedEffect(ready) {
        if (ready) snapshotFlow { listState.firstVisibleItemIndex }.collect { onVisibleIndexChanged(it) }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }, onLongPress = { onLongPress() }) },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items = pages, key = { _, p -> "${p.chapterId}_${p.pageInChapter}" }) { index, loadedPage ->
                WebtoonPageItem(page = loadedPage.page, index = index)
            }
        }
        org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** Paged reader: one page per screen. Standard (LTR), right-to-left, or vertical; each page is zoomable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedReader(
    pages: List<LoadedPage>,
    mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode,
    scrollToIndex: Int,
    scrollToken: Int,
    onVisibleIndexChanged: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isVertical = mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.VERTICAL
    val isReversed = mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.REVERSED
    val lastIndex = (pages.size - 1).coerceAtLeast(0)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = scrollToIndex.coerceIn(0, lastIndex),
        pageCount = { pages.size },
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToken) {
        if (pages.isNotEmpty()) pagerState.scrollToPage(scrollToIndex.coerceIn(0, pages.lastIndex))
        ready = true
    }
    LaunchedEffect(ready) {
        if (ready) snapshotFlow { pagerState.currentPage }.collect { onVisibleIndexChanged(it) }
    }

    val leftDelta = if (isReversed) +1 else -1
    val rightDelta = if (isReversed) -1 else +1
    fun navigate(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        scope.launch { pagerState.animateScrollToPage(target) }
    }
    val onTap: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize) -> Unit = { offset, size ->
        if (isVertical) {
            val third = size.height / 3f
            when {
                offset.y < third -> navigate(-1)
                offset.y > 2 * third -> navigate(+1)
                else -> onToggleControls()
            }
        } else {
            val third = size.width / 3f
            when {
                offset.x < third -> navigate(leftDelta)
                offset.x > 2 * third -> navigate(rightDelta)
                else -> onToggleControls()
            }
        }
    }

    // Desktop / hardware-keyboard navigation: arrows page through.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val keyboardModifier = Modifier
        .fillMaxSize()
        // Re-acquire keyboard focus on any press (focus is lost after using the config/chapter sheet).
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                runCatching { focusRequester.requestFocus() }
            }
        }
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> if (!isVertical) { navigate(leftDelta); true } else false
                Key.DirectionRight -> if (!isVertical) { navigate(rightDelta); true } else false
                Key.DirectionUp -> if (isVertical) { navigate(-1); true } else false
                Key.DirectionDown -> if (isVertical) { navigate(+1); true } else false
                else -> false
            }
        }

    Box(modifier = keyboardModifier) {
        if (isVertical) {
            androidx.compose.foundation.pager.VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomablePage(pages.getOrNull(page)?.page?.url, onTap, onLongPress)
            }
        } else {
            // Right-to-left is done by flipping the layout direction (reliable swipe paging),
            // not reverseLayout (which doesn't drive the pager's gesture correctly here).
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (isReversed) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    // Keep page content laid out LTR regardless of the pager's RTL flow.
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
                    ) {
                        ZoomablePage(pages.getOrNull(page)?.page?.url, onTap, onLongPress)
                    }
                }
            }
        }
    }
}

/**
 * A single page: single tap is forwarded for tap-navigation / control toggle, double-tap toggles zoom.
 * (Pinch-pan is intentionally NOT handled here — a per-page pan gesture swallows the Pager's swipe;
 * coexisting pinch-zoom + paging is a follow-up refinement, see MIGRATION.md R4.)
 */
@Composable
private fun ZoomablePage(
    url: String?,
    onTap: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    onLongPress: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap(it, size) },
                    onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error) } },
        )
    }
}

@Composable
private fun WebtoonPageItem(page: MangaPage, index: Int) {
    var retryHash by remember { mutableIntStateOf(0) }
    key(retryHash) {
        SubcomposeAsyncImage(
            model = page.url,
            contentDescription = "Page $index",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            loading = {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { retryHash++ }) { Text(stringResource(Res.string.retry)) }
                }
            },
        )
    }
}
