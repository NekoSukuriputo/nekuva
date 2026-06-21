package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
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
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.PlayArrow
import coil3.compose.AsyncImage
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.core.prefs.ReaderControl
import org.nekosukuriputo.nekuva.reader.domain.ReaderColorFilter
import org.nekosukuriputo.nekuva.reader.domain.toComposeColorFilter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.parsers.model.MangaPage
import org.nekosukuriputo.nekuva.core.model.isNsfw
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onResolveCloudFlare: (url: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val readerMode by viewModel.readerMode.collectAsState()
    val pageIndicator by viewModel.pageIndicator.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val colorFilterState by viewModel.colorFilter.collectAsState()
    val composeColorFilter = remember(colorFilterState) { colorFilterState.toComposeColorFilter() }
    val isIncognito by viewModel.isIncognito.collectAsState()

    // NSFW incognito prompt (Doki asks when the pref is ASK and the manga is NSFW).
    var showNsfwIncognitoDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.askNsfwIncognito.collect { showNsfwIncognitoDialog = true } }

    // Save-page feedback (Doki shows a toast; we use a snackbar).
    val snackbarHost = remember { SnackbarHostState() }
    val pageSavedMsg = stringResource(Res.string.page_saved)
    val errorMsg = stringResource(Res.string.error_occurred)
    LaunchedEffect(Unit) {
        viewModel.pageSaveEvent.collect { path ->
            // Show where the page was written (Doki shows the location in a toast).
            snackbarHost.showSnackbar(if (path != null) "$pageSavedMsg: $path" else errorMsg)
        }
    }
    // Reader toasts: bookmark add/remove + chapter change (Doki's ReaderToastView).
    val bookmarkAddedMsg = stringResource(Res.string.bookmark_added)
    val bookmarkRemovedMsg = stringResource(Res.string.bookmark_removed)
    val incognitoMsg = stringResource(Res.string.incognito_mode)
    LaunchedEffect(Unit) {
        viewModel.toast.collect { t ->
            snackbarHost.showSnackbar(
                when (t) {
                    ReaderToast.BookmarkAdded -> bookmarkAddedMsg
                    ReaderToast.BookmarkRemoved -> bookmarkRemovedMsg
                    is ReaderToast.Chapter -> t.name
                    ReaderToast.Incognito -> incognitoMsg
                },
            )
        }
    }

    // Applied reader settings (R2)
    val settings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
    val readerBackground = remember { settings.readerBackground }
    val zoomMode = remember { settings.zoomMode }
    val pageContentScale = remember(zoomMode) { zoomMode.toContentScale() }
    val pageAlignment = remember(zoomMode) {
        if (zoomMode == org.nekosukuriputo.nekuva.core.model.ZoomMode.KEEP_START) Alignment.TopCenter else Alignment.Center
    }
    val showInfoBar = remember { settings.prefBoolean("reader_bar", false) }
    val infoBarTransparent = remember { settings.isReaderBarTransparent }
    val showPageNumbers = remember { settings.prefBoolean("pages_numbers", false) }
    val webtoonGaps = remember { settings.isWebtoonGapsEnabled }
    // Webtoon zoom (Doki webtoon_zoom + webtoon_zoom_out): gate pinch-zoom + default zoom-out percent.
    val webtoonZoomEnabled = remember { settings.prefBoolean("webtoon_zoom", true) }
    val webtoonZoomOut = remember { settings.prefInt("webtoon_zoom_out", 0).coerceIn(0, 50) }
    // On-screen zoom buttons (Doki reader_zoom_buttons / ZoomControl): +/- step the CURRENT page's zoom.
    val zoomButtonsEnabled = remember { settings.isReaderZoomButtonsEnabled }
    val zoomCommands = remember { MutableSharedFlow<Float>(extraBufferCapacity = 4) }
    // Page preload policy (Doki pages_preload): always / only-on-Wi-Fi (non-metered) / never. The pref
    // stores the entry index 0=always, 1=wifi, 2=never (Doki default = Wi-Fi). Mirrors Doki's prefetch gate.
    val networkState = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.os.NetworkState>()
    val preloadAllowed = remember {
        when (settings.prefString("pages_preload", "1").toIntOrNull() ?: 1) {
            0 -> true                      // always
            1 -> !networkState.isMetered() // only on un-metered (Wi-Fi/Ethernet)
            else -> false                  // never
        }
    }
    val animatePages = remember { settings.readerAnimation != org.nekosukuriputo.nekuva.core.prefs.ReaderAnimation.NONE }
    val doubleOnLandscape = remember { settings.isReaderDoubleOnLandscape }
    // Doki auto_double_foldable: two-page automatically when the device is in foldable book posture.
    val doubleOnFoldable = remember { settings.isReaderDoubleOnFoldable }
    val isBookPosture = org.nekosukuriputo.nekuva.reader.domain.rememberIsBookPosture()
    val wideSensitivity = remember { settings.readerDoublePagesSensitivity }
    val controls = remember { viewModel.readerControls }
    // Configurable tap zones (Doki TapGridSettings). tapsReversed = follow RTL reading direction.
    val tapGridSettings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.reader.data.TapGridSettings>()
    val tapsReversed = remember(readerMode) {
        readerMode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.REVERSED && !settings.isReaderControlAlwaysLTR
    }

    // Image pipeline options (Doki: crop pages [per mode] + 32-bit color). Bumped when toggled in-sheet.
    var imageOptionsVersion by remember { mutableStateOf(0) }
    val readerImageOptions = remember(readerMode, imageOptionsVersion) {
        ReaderImageOptions(
            crop = settings.isPagesCropEnabled(readerMode),
            enhancedColors = settings.is32BitColorsEnabled,
            optimize = settings.isReaderOptimizationEnabled,
        )
    }
    // Preferred image server / mirror (Doki's ImageServerDelegate) — shown only when the source has it.
    val imageServer by viewModel.imageServer.collectAsState()
    var showImageServerDialog by remember { mutableStateOf(false) }

    // Screenshots policy (Doki): block screenshots in the reader for NSFW content / incognito sessions.
    val screenshotsPolicy = remember { settings.screenshotsPolicy }
    val mangaIsNsfw = (uiState as? ReaderUiState.Success)?.manga?.isNsfw() == true
    org.nekosukuriputo.nekuva.core.ui.SecureScreenEffect(
        secure = (screenshotsPolicy == org.nekosukuriputo.nekuva.core.prefs.ScreenshotsPolicy.BLOCK_NSFW && mangaIsNsfw) ||
            (screenshotsPolicy == org.nekosukuriputo.nekuva.core.prefs.ScreenshotsPolicy.BLOCK_INCOGNITO && isIncognito),
    )

    // Doki-style reader overlay: tap toggles the app bar + the bottom actions bar.
    var controlsVisible by remember { mutableStateOf(true) }
    var showConfigSheet by remember { mutableStateOf(false) } // reader options (3-dot / long-press)
    var showChaptersSheet by remember { mutableStateOf(false) } // chapter list (bottom button)
    var showColorSheet by remember { mutableStateOf(false) }    // colour correction panel
    var autoScrollActive by remember { mutableStateOf(false) }  // auto-scroll (ScrollTimer)
    var autoScrollSpeed by remember { mutableFloatStateOf(settings.readerAutoscrollSpeed.coerceIn(0f, 1f)) }

    // Platform window controls (Android: keep-screen-on / fullscreen / orientation; Desktop: no-op).
    val windowController = rememberReaderWindowController()
    // Volume-key page navigation events (Android forwards via MainActivity; Desktop never emits).
    val navEvents = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }
    DisposableEffect(Unit) {
        windowController.apply(
            keepScreenOn = settings.isReaderKeepScreenOn,
            fullscreen = settings.isReaderFullscreenEnabled,
            orientationIndex = settings.readerScreenOrientation,
        )
        if (settings.isReaderVolumeButtonsEnabled) {
            val inverted = settings.isReaderNavigationInverted
            ReaderKeyEvents.volumeKeyHandler = { up ->
                // Doki: volume-up = previous page by default (inverted swaps it).
                navEvents.tryEmit(if (up) (if (inverted) 1 else -1) else (if (inverted) -1 else 1))
                true
            }
        }
        onDispose {
            windowController.reset()
            ReaderKeyEvents.volumeKeyHandler = null
        }
    }

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
                                if (isIncognito) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(Res.string.incognito_mode),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else if (state.chaptersTotal > 0 && state.currentChapterIndex >= 0) {
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
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { paddingValues ->
        when (val state = uiState) {
            is ReaderUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is ReaderUiState.Error -> ErrorState(
                error = state.exception,
                onRetry = { viewModel.retry() },
                modifier = Modifier.padding(paddingValues),
                onResolveCloudFlare = { onResolveCloudFlare(it.url) },
            )
            is ReaderUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize().background(readerBackgroundColor(readerBackground))) {
                  CompositionLocalProvider(
                      LocalReaderImageOptions provides readerImageOptions,
                      LocalReaderMangaSource provides state.manga.source,
                      LocalReaderPageUrlResolver provides viewModel::resolvePageUrl,
                  ) {
                    ReaderContent(
                        pages = state.pages,
                        mode = readerMode,
                        paddingValues = paddingValues,
                        scrollToIndex = state.scrollToIndex,
                        scrollToken = state.scrollToken,
                        colorFilter = composeColorFilter,
                        autoScroll = autoScrollActive,
                        autoScrollSpeed = autoScrollSpeed,
                        contentScale = pageContentScale,
                        pageAlignment = pageAlignment,
                        webtoonGaps = webtoonGaps,
                        webtoonZoomEnabled = webtoonZoomEnabled,
                        webtoonZoomOut = webtoonZoomOut,
                        animatePages = animatePages,
                        doubleOnLandscape = doubleOnLandscape,
                        forceDouble = doubleOnFoldable && isBookPosture,
                        wideSensitivity = wideSensitivity,
                        tapGridSettings = tapGridSettings,
                        tapsReversed = tapsReversed,
                        navEvents = navEvents,
                        zoomCommands = zoomCommands,
                        preloadAllowed = preloadAllowed,
                        onVisibleIndexChanged = { viewModel.onVisibleIndexChanged(it) },
                        onVisibleBounds = { first, last, allowPrepend -> viewModel.onVisibleBounds(first, last, allowPrepend) },
                        onChapter = { viewModel.goToChapter(it) },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onShowMenu = { showConfigSheet = true },
                    )
                  }
                    // Bottom stack: thin info bar (always, if enabled) + actions bar (only when controls shown).
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(paddingValues),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showInfoBar && pageIndicator.total > 0) {
                            ReaderInfoBar(indicator = pageIndicator, transparent = infoBarTransparent)
                        } else if (showPageNumbers && pageIndicator.total > 0) {
                            Text(
                                text = "${pageIndicator.page} / ${pageIndicator.total}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        // Auto-scroll speed control stays visible while active so it can be stopped.
                        if (autoScrollActive) {
                            AutoScrollControl(
                                speed = autoScrollSpeed,
                                onSpeedChange = { autoScrollSpeed = it; settings.readerAutoscrollSpeed = it },
                                onStop = { autoScrollActive = false },
                            )
                        }
                        AnimatedVisibility(visible = controlsVisible) {
                            ReaderActionsBar(
                                controls = controls,
                                indicator = pageIndicator,
                                hasPrev = state.hasPrev,
                                hasNext = state.hasNext,
                                isBookmarked = isBookmarked,
                                autoScrollActive = autoScrollActive,
                                onPrevChapter = { viewModel.goToChapter(-1) },
                                onNextChapter = { viewModel.goToChapter(1) },
                                onSeek = { viewModel.seekToPageInCurrentChapter(it) },
                                onOpenChapters = { showChaptersSheet = true },
                                onToggleBookmark = { viewModel.toggleBookmark() },
                                onSavePage = { viewModel.savePage() },
                                onToggleAutoScroll = { autoScrollActive = !autoScrollActive },
                                rotateSupported = windowController.supportsOrientation,
                                onRotate = { windowController.toggleOrientationLock() },
                            )
                        }
                    }
                    // On-screen zoom buttons (Doki ZoomControl): bottom-end, shown with the controls.
                    if (zoomButtonsEnabled) {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(paddingValues),
                        ) {
                            ZoomButtons(
                                modifier = Modifier.padding(end = 16.dp, bottom = 88.dp),
                                onZoomIn = { zoomCommands.tryEmit(1.2f) },
                                onZoomOut = { zoomCommands.tryEmit(0.8f) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfigSheet) {
        ReaderConfigSheet(
            settings = settings,
            isBookmarked = isBookmarked,
            isIncognito = isIncognito,
            onToggleIncognito = { viewModel.toggleIncognito() },
            mode = readerMode,
            onSelectMode = {
                viewModel.setReaderMode(it)
                // Close the sheet so the reader swaps modes WITHOUT the modal scrim still on top — leaving
                // it open made the fresh paged reader compose under the modal and intermittently lost its
                // taps/long-press (app bar + chapter button wouldn't toggle until an app restart).
                showConfigSheet = false
            },
            onToggleBookmark = {
                viewModel.toggleBookmark()
                showConfigSheet = false
            },
            onSavePage = {
                viewModel.savePage()
                showConfigSheet = false
            },
            onSharePage = {
                viewModel.sharePage()
                showConfigSheet = false
            },
            onAutoScroll = {
                autoScrollActive = !autoScrollActive
                showConfigSheet = false
            },
            rotateSupported = windowController.supportsOrientation,
            onRotate = {
                windowController.toggleOrientationLock()
                showConfigSheet = false
            },
            onColorCorrection = {
                showConfigSheet = false
                showColorSheet = true
            },
            imageServerAvailable = imageServer != null,
            onImageServer = {
                showConfigSheet = false
                showImageServerDialog = true
            },
            onImageOptionsChanged = { imageOptionsVersion++ },
            onOpenSettings = {
                showConfigSheet = false
                onOpenSettings()
            },
            onDismiss = { showConfigSheet = false },
        )
    }
    if (showImageServerDialog) {
        val state = imageServer
        if (state != null) {
            ImageServerDialog(
                state = state,
                onSelect = {
                    viewModel.setImageServer(it)
                    showImageServerDialog = false
                },
                onDismiss = { showImageServerDialog = false },
            )
        } else {
            showImageServerDialog = false
        }
    }
    if (showColorSheet) {
        ColorCorrectionSheet(
            current = colorFilterState,
            onChange = { viewModel.setColorFilter(it) },
            onDismiss = { showColorSheet = false },
        )
    }
    if (showNsfwIncognitoDialog) {
        var dontAskAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                viewModel.setIncognito(false, dontAskAgain)
                showNsfwIncognitoDialog = false
            },
            icon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
            title = { Text(stringResource(Res.string.incognito_mode)) },
            text = {
                Column {
                    Text(stringResource(Res.string.incognito_mode_hint_nsfw))
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dontAskAgain, onCheckedChange = { dontAskAgain = it })
                        Text(stringResource(Res.string.dont_ask_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setIncognito(true, dontAskAgain)
                    showNsfwIncognitoDialog = false
                }) { Text(stringResource(Res.string.incognito)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setIncognito(false, dontAskAgain)
                    showNsfwIncognitoDialog = false
                }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
    if (showChaptersSheet) {
        val state = uiState
        if (state is ReaderUiState.Success) {
            ReaderChaptersSheet(
                chapters = state.chapters,
                pages = state.pages,
                bookmarks = bookmarks,
                branches = state.branches,
                selectedBranch = state.selectedBranch,
                onBranchSelect = { viewModel.setBranch(it) },
                onChapterClick = { id -> viewModel.goToChapterById(id); showChaptersSheet = false },
                onPageClick = { index -> viewModel.jumpToPageIndex(index); showChaptersSheet = false },
                onBookmarkClick = { chapterId, page -> viewModel.goToChapterAtPage(chapterId, page); showChaptersSheet = false },
                onDismiss = { showChaptersSheet = false },
            )
        }
    }
}

/**
 * Bottom actions overlay — mirrors Doki's docked `ReaderActionsView` (a rounded card centred at the
 * bottom). Renders only the controls enabled in the reader_controls pref, in Doki's order:
 * chapters/pages, prev-chapter, page slider, next-chapter, save, auto-scroll, rotate, bookmark.
 */
@Composable
private fun ReaderActionsBar(
    controls: Set<ReaderControl>,
    indicator: PageIndicator,
    hasPrev: Boolean,
    hasNext: Boolean,
    isBookmarked: Boolean,
    autoScrollActive: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeek: (Int) -> Unit,
    onOpenChapters: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSavePage: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    rotateSupported: Boolean,
    onRotate: () -> Unit,
) {
    val hasSlider = ReaderControl.SLIDER in controls && indicator.total > 1
    // Doki's toolbar_docked: rounded card, centred, with side + bottom margins.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ReaderControl.PAGES_SHEET in controls) {
                IconButton(onClick = onOpenChapters) {
                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(Res.string.chapters_and_pages))
                }
            }
            if (ReaderControl.PREV_CHAPTER in controls) {
                IconButton(onClick = onPrevChapter, enabled = hasPrev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(Res.string.prev_chapter))
                }
            }
            if (hasSlider) {
                var sliderPos by remember(indicator.page, indicator.total) { mutableFloatStateOf((indicator.page - 1).toFloat()) }
                Text("${sliderPos.roundToInt() + 1}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = sliderPos.coerceIn(0f, (indicator.total - 1).toFloat()),
                    onValueChange = { sliderPos = it },
                    onValueChangeFinished = { onSeek(sliderPos.roundToInt()) },
                    valueRange = 0f..(indicator.total - 1).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${indicator.total}", style = MaterialTheme.typography.labelMedium)
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (ReaderControl.NEXT_CHAPTER in controls) {
                IconButton(onClick = onNextChapter, enabled = hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(Res.string.next_chapter))
                }
            }
            if (ReaderControl.SAVE_PAGE in controls) {
                IconButton(onClick = onSavePage) {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(Res.string.save_page))
                }
            }
            if (ReaderControl.TIMER in controls) {
                IconButton(onClick = onToggleAutoScroll) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = stringResource(Res.string.automatic_scroll),
                        tint = if (autoScrollActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            if (ReaderControl.SCREEN_ROTATION in controls && rotateSupported) {
                IconButton(onClick = onRotate) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = stringResource(Res.string.screen_orientation))
                }
            }
            if (ReaderControl.BOOKMARK in controls) {
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(Res.string.bookmark_add),
                    )
                }
            }
        }
    }
}

/**
 * Reader options bottom sheet — mirrors Doki's reader config sheet. Bookmark + reading mode +
 * colour correction + reader settings are functional; double-page / pull-gesture toggles persist
 * to settings (the double-page renderer itself is a later R4 increment). Save-page / auto-scroll /
 * rotate are platform actions wired in a later R4 increment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderConfigSheet(
    settings: org.nekosukuriputo.nekuva.core.prefs.AppSettings,
    isBookmarked: Boolean,
    isIncognito: Boolean,
    onToggleIncognito: () -> Unit,
    mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode,
    onSelectMode: (org.nekosukuriputo.nekuva.core.prefs.ReaderMode) -> Unit,
    onToggleBookmark: () -> Unit,
    onSavePage: () -> Unit,
    onSharePage: () -> Unit,
    onAutoScroll: () -> Unit,
    rotateSupported: Boolean,
    onRotate: () -> Unit,
    onColorCorrection: () -> Unit,
    imageServerAvailable: Boolean,
    onImageServer: () -> Unit,
    onImageOptionsChanged: () -> Unit,
    onOpenSettings: () -> Unit,
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

            PersistSwitchRow(
                title = stringResource(Res.string.incognito_mode),
                checked = isIncognito,
                onChange = { onToggleIncognito() },
            )
            MenuRow(Icons.Filled.Save, stringResource(Res.string.save_page), enabled = true, onClick = onSavePage)
            MenuRow(Icons.Filled.Share, stringResource(Res.string.share_image), enabled = true, onClick = onSharePage)
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

            // Persisted quick toggles (Doki parity). Double-page rendering itself is a later R4 increment.
            PersistSwitchRow(
                title = stringResource(Res.string.use_two_pages_landscape),
                checked = settings.isReaderDoubleOnLandscape,
                onChange = { settings.isReaderDoubleOnLandscape = it },
            )
            run {
                var pull by remember { mutableStateOf(settings.prefBoolean("webtoon_pull_gesture", false)) }
                PersistSwitchRow(
                    title = stringResource(Res.string.enable_pull_gesture_title),
                    checked = pull,
                    onChange = { settings.setPref("webtoon_pull_gesture", it); pull = it },
                )
            }
            // Crop pages (Doki, per active mode bucket) + 32-bit color — applied live to the page pipeline.
            run {
                var crop by remember(mode) { mutableStateOf(settings.isPagesCropEnabled(mode)) }
                PersistSwitchRow(
                    title = stringResource(Res.string.crop_pages),
                    checked = crop,
                    onChange = { settings.setPagesCropEnabled(mode, it); crop = it; onImageOptionsChanged() },
                )
            }
            run {
                var enhanced by remember { mutableStateOf(settings.is32BitColorsEnabled) }
                PersistSwitchRow(
                    title = stringResource(Res.string.enhanced_colors),
                    checked = enhanced,
                    onChange = { settings.is32BitColorsEnabled = it; enhanced = it; onImageOptionsChanged() },
                )
            }
            if (imageServerAvailable) {
                MenuRow(Icons.Filled.Dns, stringResource(Res.string.image_server), enabled = true, onClick = onImageServer)
            }
            if (rotateSupported) {
                MenuRow(Icons.Filled.ScreenRotation, stringResource(Res.string.rotate_screen), enabled = true, onClick = onRotate)
            }
            MenuRow(Icons.Filled.Timer, stringResource(Res.string.automatic_scroll), enabled = true, onClick = onAutoScroll)
            MenuRow(Icons.Filled.Palette, stringResource(Res.string.color_correction), enabled = true, onClick = onColorCorrection)
            MenuRow(Icons.Filled.Settings, stringResource(Res.string.settings), enabled = true, onClick = onOpenSettings)
        }
    }
}

/** Single-choice dialog to pick the preferred image server / mirror (Doki's ImageServerDelegate). */
@Composable
private fun ImageServerDialog(
    state: ImageServerUiState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val automatic = stringResource(Res.string.automatic)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.image_server)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                state.options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == state.current, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label.ifEmpty { automatic })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** In-reader colour correction panel — sliders + toggles update the per-manga filter live (Doki parity). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorCorrectionSheet(
    current: ReaderColorFilter,
    onChange: (ReaderColorFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf(current) }
    fun update(new: ReaderColorFilter) {
        filter = new
        onChange(new)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(Res.string.color_correction), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Text(stringResource(Res.string.brightness), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = filter.brightness,
                onValueChange = { update(filter.copy(brightness = it)) },
                valueRange = -1f..1f,
            )
            Text(stringResource(Res.string.contrast), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = filter.contrast,
                onValueChange = { update(filter.copy(contrast = it)) },
                valueRange = -1f..1f,
            )
            PersistSwitchRow(
                title = stringResource(Res.string.invert_colors),
                checked = filter.isInverted,
                onChange = { update(filter.copy(isInverted = it)) },
            )
            PersistSwitchRow(
                title = stringResource(Res.string.grayscale),
                checked = filter.isGrayscale,
                onChange = { update(filter.copy(isGrayscale = it)) },
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { update(ReaderColorFilter.EMPTY) },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(Res.string.reset)) }
        }
    }
}

/** Auto-scroll speed control shown while auto-scroll is running (Doki's ScrollTimerControlView). */
@Composable
private fun AutoScrollControl(speed: Float, onSpeedChange: (Float) -> Unit, onStop: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Timer, contentDescription = stringResource(Res.string.automatic_scroll))
            Text(
                text = stringResource(Res.string.speed),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel))
            }
        }
    }
}

/** Branch/translation dropdown for the chapters sheet (Doki's MangaBranch selector). */
@Composable
private fun BranchSelector(
    branches: List<ReaderBranch>,
    selectedBranch: String?,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val unknown = stringResource(Res.string.unknown)
    fun label(name: String?) = name?.takeIf { it.isNotEmpty() } ?: unknown
    val current = branches.firstOrNull { it.name == selectedBranch } ?: branches.first()
    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Translate, contentDescription = stringResource(Res.string.translation), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${label(current.name)} (${current.count})", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            branches.forEach { b ->
                DropdownMenuItem(
                    text = { Text("${label(b.name)} (${b.count})") },
                    onClick = { onSelect(b.name); open = false },
                    trailingIcon = {
                        if (b.name == selectedBranch) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
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
private fun PersistSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ReaderContent(
    pages: List<LoadedPage>,
    mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode,
    paddingValues: PaddingValues,
    scrollToIndex: Int,
    scrollToken: Int,
    colorFilter: ColorFilter?,
    autoScroll: Boolean,
    autoScrollSpeed: Float,
    contentScale: ContentScale,
    pageAlignment: Alignment,
    webtoonGaps: Boolean,
    webtoonZoomEnabled: Boolean,
    webtoonZoomOut: Int,
    animatePages: Boolean,
    doubleOnLandscape: Boolean,
    forceDouble: Boolean,
    wideSensitivity: Float,
    tapGridSettings: org.nekosukuriputo.nekuva.reader.data.TapGridSettings,
    tapsReversed: Boolean,
    navEvents: kotlinx.coroutines.flow.SharedFlow<Int>,
    zoomCommands: kotlinx.coroutines.flow.SharedFlow<Float>,
    preloadAllowed: Boolean,
    onVisibleIndexChanged: (Int) -> Unit,
    onVisibleBounds: (first: Int, last: Int, allowPrepend: Boolean) -> Unit,
    onChapter: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onShowMenu: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // Double-page applies in landscape (Doki double_on_landscape) OR foldable book posture (forceDouble),
        // in a horizontal paged mode.
        val landscape = maxWidth > maxHeight
        val isPaged = mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.STANDARD ||
            mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.REVERSED
        val doublePage = isPaged && ((doubleOnLandscape && landscape) || forceDouble)
        if (mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.WEBTOON) {
            WebtoonReader(pages, scrollToIndex, scrollToken, colorFilter, autoScroll, autoScrollSpeed, webtoonGaps, webtoonZoomEnabled, webtoonZoomOut, navEvents, zoomCommands, preloadAllowed, onVisibleIndexChanged, onVisibleBounds, onToggleControls, onShowMenu)
        } else {
            PagedReader(pages, mode, scrollToIndex, scrollToken, colorFilter, autoScroll, autoScrollSpeed, contentScale, pageAlignment, animatePages, doublePage, wideSensitivity, tapGridSettings, tapsReversed, webtoonGaps, navEvents, zoomCommands, preloadAllowed, onVisibleIndexChanged, onVisibleBounds, onChapter, onToggleControls, onShowMenu)
        }
    }
}

/** Shared tap-zone → action dispatch (Doki ReaderControlDelegate). [pageBy] handles page nav per mode. */
private fun dispatchTapAction(
    tapGridSettings: org.nekosukuriputo.nekuva.reader.data.TapGridSettings,
    tapsReversed: Boolean,
    offset: androidx.compose.ui.geometry.Offset,
    size: androidx.compose.ui.unit.IntSize,
    isLongTap: Boolean,
    pageBy: (Int) -> Unit,
    onChapter: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onShowMenu: () -> Unit,
) {
    val area = org.nekosukuriputo.nekuva.reader.domain.tapGridAreaAt(offset.x, offset.y, size.width, size.height) ?: return
    val action = tapGridSettings.getTapAction(area, isLongTap) ?: return
    val effective = if (tapsReversed) {
        when (action) {
            org.nekosukuriputo.nekuva.reader.domain.TapAction.PAGE_NEXT -> org.nekosukuriputo.nekuva.reader.domain.TapAction.PAGE_PREV
            org.nekosukuriputo.nekuva.reader.domain.TapAction.PAGE_PREV -> org.nekosukuriputo.nekuva.reader.domain.TapAction.PAGE_NEXT
            else -> action
        }
    } else {
        action
    }
    when (effective) {
        org.nekosukuriputo.nekuva.reader.domain.TapAction.PAGE_NEXT -> pageBy(1)
        org.nekosukuriputo.nekuva.reader.domain.TapAction.PAGE_PREV -> pageBy(-1)
        org.nekosukuriputo.nekuva.reader.domain.TapAction.CHAPTER_NEXT -> onChapter(1)
        org.nekosukuriputo.nekuva.reader.domain.TapAction.CHAPTER_PREV -> onChapter(-1)
        org.nekosukuriputo.nekuva.reader.domain.TapAction.TOGGLE_UI -> onToggleControls()
        org.nekosukuriputo.nekuva.reader.domain.TapAction.SHOW_MENU -> onShowMenu()
    }
}

/** Map the user's zoom mode to a Compose [ContentScale] for paged reading (Doki parity). */
private fun org.nekosukuriputo.nekuva.core.model.ZoomMode.toContentScale(): ContentScale = when (this) {
    org.nekosukuriputo.nekuva.core.model.ZoomMode.FIT_CENTER -> ContentScale.Fit
    org.nekosukuriputo.nekuva.core.model.ZoomMode.FIT_HEIGHT -> ContentScale.FillHeight
    org.nekosukuriputo.nekuva.core.model.ZoomMode.FIT_WIDTH -> ContentScale.FillWidth
    org.nekosukuriputo.nekuva.core.model.ZoomMode.KEEP_START -> ContentScale.FillWidth
}

/** Doki-style reader chapter sheet: tabs for the chapter list, page thumbnails, and bookmarks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderChaptersSheet(
    chapters: List<ReaderChapterItem>,
    pages: List<LoadedPage>,
    bookmarks: List<Bookmark>,
    branches: List<ReaderBranch>,
    selectedBranch: String?,
    onBranchSelect: (String?) -> Unit,
    onChapterClick: (Long) -> Unit,
    onPageClick: (Int) -> Unit,
    onBookmarkClick: (Long, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) } // 0=list, 1=grid, 2=bookmarks
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            // Branch / translation selector (Doki) — only when the manga has more than one branch.
            if (branches.isNotEmpty()) {
                BranchSelector(branches, selectedBranch, onBranchSelect)
                HorizontalDivider()
            }
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
                                // Downloaded chapter → SD-card badge (Doki reader chapters list parity).
                                if (ch.isDownloaded) {
                                    Icon(
                                        Icons.Filled.SdCard,
                                        contentDescription = stringResource(Res.string.on_device),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
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

/** Thin bottom info bar (Doki's ReaderInfoBarView): chapter name · clock · battery · page x/total.
 *  [transparent] (Doki reader_bar_transparent) drops the background fill so it overlays the page. */
@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun ReaderInfoBar(indicator: PageIndicator, transparent: Boolean, modifier: Modifier = Modifier) {
    val battery = rememberBatteryPercent()
    var clock by remember { mutableStateOf(currentClockLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClockLabel()
            delay(15_000)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
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
        Spacer(Modifier.width(12.dp))
        Text(clock, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (battery != null) {
            Spacer(Modifier.width(10.dp))
            Text("$battery%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${indicator.page} / ${indicator.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun currentClockLabel(): String {
    // Use kotlin.time.Clock/Instant (stdlib), NOT kotlinx.datetime.Instant — the latter was removed in
    // kotlinx-datetime 0.7+ and throws NoClassDefFoundError on Desktop at runtime (CLAUDE.md §4.6).
    val dt = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    fun pad(v: Int) = v.toString().padStart(2, '0')
    return "${pad(dt.hour)}:${pad(dt.minute)}"
}

/** Floating zoom +/- buttons (Doki's ZoomControl) — stacked vertically, bottom-end of the reader. */
@Composable
private fun ZoomButtons(modifier: Modifier = Modifier, onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    Column(modifier = modifier) {
        FilledTonalIconButton(onClick = onZoomIn) {
            Icon(Icons.Filled.ZoomIn, contentDescription = stringResource(Res.string.zoom_in))
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalIconButton(onClick = onZoomOut) {
            Icon(Icons.Filled.ZoomOut, contentDescription = stringResource(Res.string.zoom_out))
        }
    }
}

/**
 * Warms Coil's cache for the next [PRELOAD_AHEAD] pages after [currentIndex] (Doki's PageLoader.prefetch),
 * using the same request flags as the on-screen pages so they hit the cache. A no-op when preloading is
 * not allowed by the network policy (Doki pages_preload).
 */
@Composable
private fun ReaderPagePreloader(pages: List<LoadedPage>, currentIndex: Int, enabled: Boolean) {
    if (!enabled) return
    val context = coil3.compose.LocalPlatformContext.current
    val options = LocalReaderImageOptions.current
    val source = LocalReaderMangaSource.current
    val loader = remember(context) { coil3.SingletonImageLoader.get(context) }
    LaunchedEffect(currentIndex, pages.size, options, source) {
        val start = (currentIndex + 1).coerceAtLeast(0)
        val end = (start + PRELOAD_AHEAD).coerceAtMost(pages.size)
        for (i in start until end) {
            val url = pages.getOrNull(i)?.page?.url ?: continue
            loader.enqueue(buildReaderPageRequest(context, url, options, foreground = false, source = source))
        }
    }
}

// Pages to warm into Coil's cache ahead of the current page (Doki PageLoader PREFETCH_LIMIT = 6/10). With
// the manga client's CacheLimitInterceptor forcing a min cache age, these stick on disk so slow CDNs
// (e.g. desu.photos) don't re-download on scroll-back.
private const val PRELOAD_AHEAD = 5

/** Continuous vertical reader (full-width images, multi-chapter append). */
@Composable
private fun WebtoonReader(
    pages: List<LoadedPage>,
    scrollToIndex: Int,
    scrollToken: Int,
    colorFilter: ColorFilter?,
    autoScroll: Boolean,
    autoScrollSpeed: Float,
    webtoonGaps: Boolean,
    webtoonZoomEnabled: Boolean,
    webtoonZoomOut: Int,
    navEvents: kotlinx.coroutines.flow.SharedFlow<Int>,
    zoomCommands: kotlinx.coroutines.flow.SharedFlow<Float>,
    preloadAllowed: Boolean,
    onVisibleIndexChanged: (Int) -> Unit,
    onVisibleBounds: (first: Int, last: Int, allowPrepend: Boolean) -> Unit,
    onToggleControls: () -> Unit,
    onShowMenu: () -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Preload upcoming pages into Coil's cache (Doki prefetch), gated by the network policy.
    ReaderPagePreloader(pages, listState.firstVisibleItemIndex, preloadAllowed)
    // Desktop keyboard scroll: arrow Up/Down + PageUp/Down + Space scroll by a fraction of the VISIBLE
    // viewport (not a whole page image), so reading stays continuous even in a half-height window.
    val focusRequester = remember { FocusRequester() }
    val keyScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    fun scrollByKey(dir: Int) {
        val vp = listState.layoutInfo.viewportSize.height.takeIf { it > 0 } ?: 1200
        keyScope.launch { listState.animateScrollBy(vp * 0.85f * dir) }
    }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToken) {
        if (pages.isNotEmpty()) listState.scrollToItem(scrollToIndex.coerceIn(0, pages.lastIndex))
        ready = true
    }
    // Track first AND last visible page (Doki uses both to load adjacent chapters at the boundary).
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            val first = info.firstOrNull()?.index ?: 0
            val last = info.lastOrNull()?.index ?: first
            first to last
        }.collect { (first, last) ->
            onVisibleIndexChanged(first)
            onVisibleBounds(first, last, true)
        }
    }
    // Auto-scroll: continuously nudge the list while active. Speed maps to px/second (Doki's ScrollTimer).
    LaunchedEffect(autoScroll, autoScrollSpeed) {
        if (autoScroll) {
            val pixelsPerSecond = 30f + autoScrollSpeed.coerceIn(0f, 1f) * 470f
            while (isActive) {
                listState.scrollBy(pixelsPerSecond / 60f)
                delay(16)
            }
        }
    }
    // Volume-key navigation in webtoon = jump ~one viewport up/down.
    LaunchedEffect(Unit) {
        navEvents.collect { delta ->
            val vp = listState.layoutInfo.viewportSize.height
            listState.animateScrollBy((if (vp > 0) vp * 0.9f else 1200f) * delta)
        }
    }
    // Pinch-zoom for the continuous strip (Doki's webtoon zoom). The whole LazyColumn is scaled via
    // graphicsLayer; vertical drags fall through to the list scroll, while pinch + horizontal pan are
    // handled here. Double-tap toggles minScale / 2×.
    // Doki webtoon_zoom gates whether the strip is zoomable at all; webtoon_zoom_out lets the strip
    // rest zoomed-OUT below 100% by that percent (minScale), so it starts there. Disabled → fixed 1×.
    val minScale = if (webtoonZoomEnabled) (1f - webtoonZoomOut / 100f) else 1f
    val maxScale = if (webtoonZoomEnabled) 3f else 1f
    var scale by remember(minScale) { mutableStateOf(minScale) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    fun maxPanX() = (size.width * (scale - 1f)).coerceAtLeast(0f) / 2f
    // On-screen zoom buttons (Doki ZoomControl) step the strip's scale, like a pinch.
    LaunchedEffect(webtoonZoomEnabled, minScale, maxScale) {
        if (!webtoonZoomEnabled) return@LaunchedEffect
        zoomCommands.collect { factor ->
            scale = (scale * factor).coerceIn(minScale, maxScale)
            offsetX = offsetX.coerceIn(-maxPanX(), maxPanX())
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            // Desktop keyboard scrolling (re-acquire focus on any press, like the paged reader).
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    runCatching { focusRequester.requestFocus() }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    androidx.compose.ui.input.key.Key.DirectionDown,
                    androidx.compose.ui.input.key.Key.PageDown,
                    androidx.compose.ui.input.key.Key.Spacebar -> { scrollByKey(1); true }
                    androidx.compose.ui.input.key.Key.DirectionUp,
                    androidx.compose.ui.input.key.Key.PageUp -> { scrollByKey(-1); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                // Webtoon is continuous-scroll, so any tap toggles the UI (Doki-like); long-press = menu.
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (!webtoonZoomEnabled) return@detectTapGestures
                        if (scale > minScale) { scale = minScale; offsetX = 0f } else { scale = 2f }
                    },
                    onLongPress = { onShowMenu() },
                )
            }
            // Pinch (2 fingers) zooms; while zoomed a horizontal one-finger drag pans. Vertical drags
            // are NOT consumed, so the LazyColumn keeps scrolling normally.
            .pointerInput(webtoonZoomEnabled, minScale, maxScale) {
                if (!webtoonZoomEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                scale = (scale * zoom).coerceIn(minScale, maxScale)
                                offsetX = offsetX.coerceIn(-maxPanX(), maxPanX())
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (scale > 1f) {
                            val pan = event.calculatePan()
                            if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                offsetX = (offsetX + pan.x).coerceIn(-maxPanX(), maxPanX())
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX),
            verticalArrangement = if (webtoonGaps) Arrangement.spacedBy(12.dp) else Arrangement.Top,
        ) {
            itemsIndexed(items = pages, key = { _, p -> "${p.chapterId}_${p.pageInChapter}" }) { index, loadedPage ->
                WebtoonPageItem(page = loadedPage.page, index = index, colorFilter = colorFilter)
            }
        }
        org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * Paged reader: one page per screen (Standard/RTL/Vertical, zoomable) OR two pages per screen when
 * [doublePage] is on (landscape spreads, Doki). Internally it pages over "units": each unit is one
 * page (single mode — identical to before) or a spread of up to two pages (cover stays solo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedReader(
    pages: List<LoadedPage>,
    mode: org.nekosukuriputo.nekuva.core.prefs.ReaderMode,
    scrollToIndex: Int,
    scrollToken: Int,
    colorFilter: ColorFilter?,
    autoScroll: Boolean,
    autoScrollSpeed: Float,
    contentScale: ContentScale,
    pageAlignment: Alignment,
    animatePages: Boolean,
    doublePage: Boolean,
    wideSensitivity: Float,
    tapGridSettings: org.nekosukuriputo.nekuva.reader.data.TapGridSettings,
    tapsReversed: Boolean,
    pageGaps: Boolean,
    navEvents: kotlinx.coroutines.flow.SharedFlow<Int>,
    zoomCommands: kotlinx.coroutines.flow.SharedFlow<Float>,
    preloadAllowed: Boolean,
    onVisibleIndexChanged: (Int) -> Unit,
    onVisibleBounds: (first: Int, last: Int, allowPrepend: Boolean) -> Unit,
    onChapter: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onShowMenu: () -> Unit,
) {
    val isVertical = mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.VERTICAL
    // Gap between pages in paged modes (Doki page margin) — reuse the "gaps" setting shared with webtoon.
    val pageSpacing = if (pageGaps) 16.dp else 0.dp
    val isReversed = mode == org.nekosukuriputo.nekuva.core.prefs.ReaderMode.REVERSED
    val useDouble = doublePage && !isVertical
    // Wide-page detection (Doki double-page: a landscape/spread page is shown SOLO, not paired). Each page's
    // aspect ratio is learned from Coil as it loads; a page wider than the sensitivity-tuned threshold is wide.
    // Higher sensitivity → lower threshold → more pages treated as wide spreads.
    val wideThreshold = remember(wideSensitivity) { 1f + (1f - wideSensitivity.coerceIn(0f, 1f)) * 0.3f }
    val wideFlags = remember(pages.size) { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    val onAspect: (Int, Float) -> Unit = remember(wideThreshold) {
        { index, aspect -> if (index in pages.indices) wideFlags[index] = aspect >= wideThreshold }
    }
    // Display units: each is one page (single) or a spread of up to two (double, cover + wide pages solo).
    val wideKey = if (useDouble) wideFlags.entries.count { it.value } else 0
    val units = remember(pages.size, useDouble, wideKey, wideThreshold) {
        if (!useDouble) {
            List(pages.size) { intArrayOf(it) }
        } else {
            buildList {
                if (pages.isNotEmpty()) {
                    add(intArrayOf(0)) // cover page solo
                    var i = 1
                    while (i < pages.size) {
                        // A wide page (or one whose partner is wide) stands alone — never half a pair.
                        if (i + 1 < pages.size && wideFlags[i] != true && wideFlags[i + 1] != true) {
                            add(intArrayOf(i, i + 1)); i += 2
                        } else {
                            add(intArrayOf(i)); i += 1
                        }
                    }
                }
            }
        }
    }
    val pageToUnit = remember(units) {
        IntArray(pages.size).also { arr -> units.forEachIndexed { u, idxs -> idxs.forEach { if (it < arr.size) arr[it] = u } } }
    }
    fun unitFirstPage(u: Int) = units.getOrNull(u)?.firstOrNull() ?: 0
    fun unitLastPage(u: Int) = units.getOrNull(u)?.lastOrNull() ?: 0
    fun pageToUnitIdx(p: Int) = pageToUnit.getOrElse(p.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { 0 }
    val lastUnit = (units.size - 1).coerceAtLeast(0)

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = pageToUnitIdx(scrollToIndex).coerceIn(0, lastUnit),
        pageCount = { units.size },
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    // While a page is zoomed, disable pager swiping so panning the page doesn't flip pages.
    var pageZoomed by remember { mutableStateOf(false) }
    // Preload upcoming pages into Coil's cache (Doki prefetch), gated by the network policy.
    ReaderPagePreloader(pages, unitLastPage(pagerState.currentPage), preloadAllowed)
    LaunchedEffect(scrollToken) {
        if (units.isNotEmpty()) pagerState.scrollToPage(pageToUnitIdx(scrollToIndex).coerceIn(0, lastUnit))
        ready = true
    }
    LaunchedEffect(ready) {
        if (ready) snapshotFlow { pagerState.currentPage }.collect { u ->
            onVisibleIndexChanged(unitFirstPage(u))
            onVisibleBounds(unitFirstPage(u), unitLastPage(u), false) // paged: forward-append only
        }
    }
    // Reset zoom-lock when the page changes (e.g. via keyboard/volume while not interacting).
    LaunchedEffect(pagerState.currentPage) { pageZoomed = false }
    // Auto-scroll for paged modes = auto-advance after a delay (shorter delay = faster).
    LaunchedEffect(autoScroll, autoScrollSpeed) {
        if (autoScroll) {
            val delayMs = (8000L - (autoScrollSpeed.coerceIn(0f, 1f) * 6500f).toLong()).coerceAtLeast(1000L)
            while (isActive) {
                delay(delayMs)
                val target = pagerState.currentPage + 1
                if (target <= lastUnit) pagerState.animateScrollToPage(target) else break
            }
        }
    }

    val leftDelta = if (isReversed) +1 else -1
    val rightDelta = if (isReversed) -1 else +1
    fun navigate(delta: Int) {
        val target = (pagerState.currentPage + delta).coerceIn(0, lastUnit)
        scope.launch {
            // Page-transition animation pref: animate, or jump instantly when disabled (Doki "none").
            if (animatePages) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }
    // Volume-key navigation in paged modes = unit by ±1.
    LaunchedEffect(Unit) { navEvents.collect { navigate(it) } }
    // Configurable tap-grid dispatch (Doki). pageBy(+1)=next unit; the tapsReversed flag handles RTL.
    val onTapGrid: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize, Boolean) -> Unit = { offset, size, isLong ->
        dispatchTapAction(tapGridSettings, tapsReversed, offset, size, isLong, { navigate(it) }, onChapter, onToggleControls, onShowMenu)
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
                // Extra keys (Doki): space / Page Down / 'R' = next, Page Up / 'L' = previous.
                Key.Spacebar, Key.PageDown, Key.R -> { navigate(+1); true }
                Key.PageUp, Key.L -> { navigate(-1); true }
                else -> false
            }
        }

    Box(modifier = keyboardModifier) {
        if (isVertical) {
            androidx.compose.foundation.pager.VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !pageZoomed,
                pageSpacing = pageSpacing,
            ) { u ->
                // Resolve getPageUrl (some sources return an intermediate page URL) — else blank in vertical mode.
                ZoomablePage(rememberResolvedPageUrl(pages.getOrNull(unitFirstPage(u))?.page), colorFilter, contentScale, pageAlignment, u == pagerState.currentPage, zoomCommands, { pageZoomed = it }, onTapGrid)
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
                    userScrollEnabled = !pageZoomed,
                    pageSpacing = pageSpacing,
                ) { u ->
                    // Keep page content laid out LTR regardless of the pager's RTL flow.
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
                    ) {
                        val unitPages = units.getOrNull(u)
                        if (useDouble && unitPages != null && unitPages.size == 2) {
                            DoublePageSpread(unitPages, pages, isReversed, colorFilter, contentScale, u == pagerState.currentPage, zoomCommands, { pageZoomed = it }, onTapGrid, onAspect)
                        } else {
                            val pageIndex = unitPages?.firstOrNull() ?: 0
                            ZoomablePage(rememberResolvedPageUrl(pages.getOrNull(pageIndex)?.page), colorFilter, contentScale, pageAlignment, u == pagerState.currentPage, zoomCommands, { pageZoomed = it }, onTapGrid)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Two pages side by side (Doki double-page). RTL puts the lower page on the right. Tap zones map to the
 * full spread. Pinch-zoom + pan apply to the whole spread (double-tap toggles 1×/2.5×); while zoomed the
 * parent pager stops swiping (via [onZoomChanged]) so panning never flips the spread.
 */
@Composable
private fun DoublePageSpread(
    unitPages: IntArray,
    pages: List<LoadedPage>,
    isReversed: Boolean,
    colorFilter: ColorFilter?,
    contentScale: ContentScale,
    active: Boolean,
    zoomCommands: kotlinx.coroutines.flow.SharedFlow<Float>,
    onZoomChanged: (Boolean) -> Unit,
    onTapGrid: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize, Boolean) -> Unit,
    onAspect: (Int, Float) -> Unit = { _, _ -> },
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val zoomed = scale > 1f
    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }
    // On-screen zoom buttons (Doki ZoomControl) act on the current spread only.
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        zoomCommands.collect { factor ->
            scale = (scale * factor).coerceIn(1f, 5f)
            val maxX = (size.width * (scale - 1f)).coerceAtLeast(0f) / 2f
            val maxY = (size.height * (scale - 1f)).coerceAtLeast(0f) / 2f
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) {
            val maxX = (size.width * (scale - 1f)) / 2f
            val maxY = (size.height * (scale - 1f)) / 2f
            Offset(
                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        } else {
            Offset.Zero
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .transformable(state = transformState, enabled = zoomed)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTapGrid(it, size, false) },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onLongPress = { onTapGrid(it, size, true) },
                )
            },
    ) {
        val ordered = if (isReversed) unitPages.reversedArray() else unitPages
        Row(
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
        ) {
            ordered.forEach { idx ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    var retryHash by remember(idx) { mutableIntStateOf(0) }
                    key(retryHash) {
                        SubcomposeAsyncImage(
                            model = rememberReaderPageModel(rememberResolvedPageUrl(pages.getOrNull(idx)?.page)),
                            contentDescription = null,
                            contentScale = contentScale,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { st -> reportAspect(st) { onAspect(idx, it) } },
                            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                            error = { PageErrorRetry { retryHash++ } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single reader page with pinch-zoom + pan over a Coil image (so all Coil decoders, incl. AVIF, apply).
 * Telephoto subsampling was dropped: it tile-decodes the raw cached file via BitmapRegionDecoder, which
 * can't read AVIF (DoujinDesu etc.) and bypasses Coil's decoders → black pages. Coil already downsamples
 * to the view size (no OOM). A one-finger drag at 1× is NOT consumed so the pager still receives swipes;
 * while zoomed the parent disables pager scroll (via [onZoomChanged]) so panning never flips the page.
 */
@Composable
private fun ZoomablePage(
    url: String?,
    colorFilter: ColorFilter?,
    contentScale: ContentScale,
    pageAlignment: Alignment,
    active: Boolean,
    zoomCommands: kotlinx.coroutines.flow.SharedFlow<Float>,
    onZoomChanged: (Boolean) -> Unit,
    onTap: (androidx.compose.ui.geometry.Offset, androidx.compose.ui.unit.IntSize, isLongTap: Boolean) -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // Per-page retry (Doki): a network-failed page shows a Refresh button that re-requests the image.
    // Keyed by url so moving to a new page resets it.
    var retryHash by remember(url) { mutableIntStateOf(0) }
    val zoomed = scale > 1f
    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        zoomCommands.collect { factor ->
            scale = (scale * factor).coerceIn(1f, 5f)
            val maxX = (size.width * (scale - 1f)).coerceAtLeast(0f) / 2f
            val maxY = (size.height * (scale - 1f)).coerceAtLeast(0f) / 2f
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) {
            val maxX = (size.width * (scale - 1f)) / 2f
            val maxY = (size.height * (scale - 1f)) / 2f
            Offset(
                (offset.x + panChange.x).coerceIn(-maxX, maxX),
                (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        } else {
            Offset.Zero
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .transformable(state = transformState, enabled = zoomed)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap(it, size, false) },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onLongPress = { onTap(it, size, true) },
                )
            },
        contentAlignment = pageAlignment,
    ) {
        key(retryHash) {
            SubcomposeAsyncImage(
                model = rememberReaderPageModel(url, foreground = active),
                contentDescription = null,
                contentScale = contentScale,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = { PageErrorRetry { retryHash++ } },
            )
        }
    }
}

/** Error slot for a reader page that failed to load: message + Refresh button (Doki per-page retry). */
@Composable
private fun PageErrorRetry(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

/** Report a loaded page's aspect ratio (width/height) from a Coil success state, for wide-page detection. */
private fun reportAspect(state: coil3.compose.AsyncImagePainter.State.Success, onAspect: (Float) -> Unit) {
    val img = state.result.image
    if (img.height > 0) onAspect(img.width.toFloat() / img.height.toFloat())
}

@Composable
private fun WebtoonPageItem(page: MangaPage, index: Int, colorFilter: ColorFilter?) {
    var retryHash by remember { mutableIntStateOf(0) }
    key(retryHash) {
        SubcomposeAsyncImage(
            model = rememberReaderPageModel(rememberResolvedPageUrl(page)),
            contentDescription = "Page $index",
            contentScale = ContentScale.FillWidth,
            colorFilter = colorFilter,
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
