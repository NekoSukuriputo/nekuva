package org.nekosukuriputo.nekuva.details.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.filled.Refresh
import org.nekosukuriputo.nekuva.core.image.mangaSourceExtra
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.UnfoldMore
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.remotelist.ui.mangaStateTitle
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import androidx.compose.ui.unit.sp
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel = koinViewModel(),
    onChapterClick: (Long, Long) -> Unit,
    onBookmarkClick: (mangaId: Long, chapterId: Long, page: Int) -> Unit,
    onPageClick: (mangaId: Long, chapterId: Long, page: Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onBackClick: () -> Unit,
    onManageCategoriesClick: () -> Unit,
    onRelatedClick: (mangaId: Long) -> Unit = {},
    onAlternativesClick: (mangaId: Long) -> Unit = {},
    onFindSimilar: (mangaId: Long) -> Unit = {},
    onOpenManga: (mangaId: Long) -> Unit = {},
    // Doki showTagDialog/showAuthorDialog: "search in source" applies a genre/author FILTER (not free text).
    onTagSearchInSource: (sourceName: String, tagKey: String, tagTitle: String) -> Unit = { _, _, _ -> },
    onAuthorSearchInSource: (sourceName: String, author: String) -> Unit = { _, _ -> },
    // Doki "Search everywhere": global search carrying the search kind (tag → TAG, author → AUTHOR).
    onGlobalSearch: (query: String, kind: org.nekosukuriputo.nekuva.search.domain.SearchKind) -> Unit = { _, _ -> },
    onOpenBrowser: (url: String) -> Unit = {},
    onResolveCloudFlare: (url: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagesState by viewModel.pagesState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val mangaCategories by viewModel.mangaCategories.collectAsState()
    val history by viewModel.history.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val relatedManga by viewModel.relatedManga.collectAsState()
    val readingTime by viewModel.readingTime.collectAsState()
    val downloadedChapterIds by viewModel.downloadedChapterIds.collectAsState()
    val scrobblingInfo by viewModel.scrobblingInfo.collectAsState()

    var showScrobblingSelector by remember { mutableStateOf(false) }
    var editingScrobblingInfo by remember { mutableStateOf<org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingInfo?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showMangaStats by remember { mutableStateOf(false) }
    var showEditOverride by remember { mutableStateOf(false) }
    var fullScreenCover by remember { mutableStateOf<String?>(null) }
    var tagDialogFor by remember { mutableStateOf<MangaTag?>(null) }
    var authorDialogFor by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val downloadStartedMsg = stringResource(Res.string.download_started)
    val downloadAddedMsg = stringResource(Res.string.download_added)
    val detailsLabel = stringResource(Res.string.details)

    // Tag/genre click (Doki showTagDialog): search this tag in the source, or everywhere.
    tagDialogFor?.let { tag ->
        val sourceName = (uiState as? DetailsUiState.Success)?.manga?.source?.name ?: tag.source.name
        val sourceTitle = org.nekosukuriputo.nekuva.parsers.model.MangaParserSource.entries
            .find { it.name == sourceName }?.title ?: sourceName
        AlertDialog(
            onDismissRequest = { tagDialogFor = null },
            title = { Text(tag.title) },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.search_on_s, sourceTitle),
                        modifier = Modifier.fillMaxWidth()
                            .clickable { tagDialogFor = null; onTagSearchInSource(sourceName, tag.key, tag.title) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.search_everywhere),
                        modifier = Modifier.fillMaxWidth()
                            .clickable { tagDialogFor = null; onGlobalSearch(tag.title, org.nekosukuriputo.nekuva.search.domain.SearchKind.TAG) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { tagDialogFor = null }) { Text(stringResource(Res.string.close)) } },
        )
    }

    // Author click (Doki showAuthorDialog): search this author in the source, or everywhere.
    authorDialogFor?.let { author ->
        val sourceName = (uiState as? DetailsUiState.Success)?.manga?.source?.name ?: ""
        val sourceTitle = org.nekosukuriputo.nekuva.parsers.model.MangaParserSource.entries
            .find { it.name == sourceName }?.title ?: sourceName
        AlertDialog(
            onDismissRequest = { authorDialogFor = null },
            title = { Text(author) },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.search_on_s, sourceTitle),
                        modifier = Modifier.fillMaxWidth()
                            .clickable { authorDialogFor = null; onAuthorSearchInSource(sourceName, author) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.search_everywhere),
                        modifier = Modifier.fillMaxWidth()
                            .clickable { authorDialogFor = null; onGlobalSearch(author, org.nekosukuriputo.nekuva.search.domain.SearchKind.AUTHOR) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { authorDialogFor = null }) { Text(stringResource(Res.string.close)) } },
        )
    }

    val editOverrideState = uiState
    if (showEditOverride && editOverrideState is DetailsUiState.Success) {
        org.nekosukuriputo.nekuva.core.ui.components.EditOverrideDialog(
            currentTitle = editOverrideState.manga.title,
            currentCoverUrl = editOverrideState.manga.coverUrl,
            onDismiss = { showEditOverride = false },
            onSave = { title, coverUrl ->
                viewModel.saveOverride(title, coverUrl)
                showEditOverride = false
            },
        )
    }

    if (showCategoryDialog) {
        CategorySelectionDialog(
            categories = allCategories,
            selectedCategories = mangaCategories,
            isFavorite = isFavorite,
            onDismiss = { showCategoryDialog = false },
            onToggleCategory = { categoryId, isSelected -> viewModel.toggleCategory(categoryId, isSelected) },
            onManageClick = {
                showCategoryDialog = false
                onManageCategoriesClick()
            }
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    fullScreenCover?.let { coverUrl ->
        val imageSaver = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.image.domain.ImageSaveUseCase>()
        val savedMsg = stringResource(Res.string.page_saved)
        val saveErrMsg = stringResource(Res.string.error_occurred)
        org.nekosukuriputo.nekuva.image.ui.FullScreenImageViewer(
            imageUrl = coverUrl,
            source = (uiState as? DetailsUiState.Success)?.manga?.source,
            onDismiss = { fullScreenCover = null },
            // Share the actual image bytes (Doki ShareHelper.shareImage), not just the URL.
            onShare = { url -> scope.launch { runCatching { imageSaver.share(url) } } },
            onSave = { url ->
                scope.launch {
                    val loc = runCatching { imageSaver.save(url) }.getOrNull()
                    scaffoldState.snackbarHostState.showSnackbar(if (loc != null) savedMsg else saveErrMsg)
                }
            },
        )
    }

    // Tracking: selector (link) sheet from the overflow, and the edit/unlink sheet from a tracking card.
    if (showScrobblingSelector) {
        org.nekosukuriputo.nekuva.details.ui.scrobbling.ScrobblingSelectorSheet(
            scrobblers = viewModel.availableScrobblers,
            mangaTitle = (uiState as? DetailsUiState.Success)?.manga?.title ?: "",
            onLink = { scrobbler, target -> viewModel.linkScrobbler(scrobbler, target) },
            onDismiss = { showScrobblingSelector = false },
        )
    }
    editingScrobblingInfo?.let { info ->
        val scrobbler = viewModel.availableScrobblers.find { it.scrobblerService == info.scrobbler }
        if (scrobbler == null) {
            editingScrobblingInfo = null
        } else {
            org.nekosukuriputo.nekuva.details.ui.scrobbling.ScrobblingInfoEditSheet(
                info = info,
                onUpdate = { rating, status -> viewModel.updateScrobbling(scrobbler, rating, status) },
                onUnlink = { viewModel.unlinkScrobbler(scrobbler) },
                onDismiss = { editingScrobblingInfo = null },
            )
        }
    }

    val successForDialog = uiState as? DetailsUiState.Success
    if (showDownloadDialog && successForDialog != null) {
        val downloadVm = koinViewModel<org.nekosukuriputo.nekuva.download.ui.dialog.DownloadDialogViewModel>(
            key = "download_${successForDialog.manga.id}",
        ) { org.koin.core.parameter.parametersOf(successForDialog.manga.id) }
        org.nekosukuriputo.nekuva.download.ui.dialog.DownloadDialog(
            viewModel = downloadVm,
            onDismiss = { showDownloadDialog = false },
            onScheduled = { started ->
                showDownloadDialog = false
                scope.launch {
                    val result = scaffoldState.snackbarHostState.showSnackbar(
                        message = if (started) downloadStartedMsg else downloadAddedMsg,
                        actionLabel = detailsLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenDownloads()
                }
            },
        )
    }

    if (showMangaStats && successForDialog != null) {
        MangaStatsDialog(mangaId = successForDialog.manga.id, onDismiss = { showMangaStats = false })
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(scaffoldState.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { }, // No title on TopAppBar as per Doki
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            (uiState as? DetailsUiState.Success)?.manga?.let {
                                org.nekosukuriputo.nekuva.core.share.shareManga(it)
                            }
                        },
                        enabled = uiState is DetailsUiState.Success,
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.share))
                    }
                    IconButton(
                        onClick = { showDownloadDialog = true },
                        enabled = uiState is DetailsUiState.Success,
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(Res.string.save_manga))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.more_options))
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.statistics)) },
                                enabled = uiState is DetailsUiState.Success,
                                onClick = {
                                    showOverflowMenu = false
                                    showMangaStats = true
                                },
                            )
                            // Tracking / scrobbling (Doki action_scrobbling) — only when a tracker is authorized.
                            if (viewModel.availableScrobblers.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.tracking)) },
                                    enabled = (uiState as? DetailsUiState.Success)?.manga?.let { !it.isLocal } == true,
                                    onClick = {
                                        showOverflowMenu = false
                                        showScrobblingSelector = true
                                    },
                                )
                            }
                            // Pin manga to launcher (Doki action_shortcut) — Android; Desktop no-op.
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.create_shortcut)) },
                                enabled = uiState is DetailsUiState.Success,
                                onClick = {
                                    showOverflowMenu = false
                                    (uiState as? DetailsUiState.Success)?.manga?.let {
                                        org.nekosukuriputo.nekuva.core.shortcuts.pinMangaShortcut(it.id, it.title)
                                    }
                                },
                            )
                            // Edit custom title / cover (Doki action_edit_override → OverrideConfig).
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.edit)) },
                                enabled = uiState is DetailsUiState.Success,
                                onClick = {
                                    showOverflowMenu = false
                                    showEditOverride = true
                                },
                            )
                            // Related/similar manga from the SAME source (Doki action_related → Find similar).
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.find_similar)) },
                                enabled = (uiState as? DetailsUiState.Success)?.manga?.let { !it.isLocal } == true,
                                onClick = {
                                    showOverflowMenu = false
                                    (uiState as? DetailsUiState.Success)?.manga?.let { onFindSimilar(it.id) }
                                },
                            )
                            // Find the same manga in other sources (Doki action_alternatives → Alternatives).
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.alternatives)) },
                                enabled = uiState is DetailsUiState.Success,
                                onClick = {
                                    showOverflowMenu = false
                                    (uiState as? DetailsUiState.Success)?.manga?.let { onAlternativesClick(it.id) }
                                },
                            )
                            // Open the online variant of a saved/local manga (Doki action_online).
                            val onlineManga = (uiState as? DetailsUiState.Success)?.manga
                            if (onlineManga != null && onlineManga.isLocal) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.online_variant)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.openOnline { id -> onOpenManga(id) }
                                    },
                                )
                            }
                            // Open the manga's web page (Doki action_browser) — remote manga only.
                            if (onlineManga != null && !onlineManga.isLocal && onlineManga.publicUrl.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.open_in_browser)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenBrowser(onlineManga.publicUrl)
                                    },
                                )
                            }
                            // Delete a saved/local manga (Doki action_delete) — local only.
                            if (onlineManga != null && onlineManga.isLocal) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.delete)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.deleteLocal { onBackClick() }
                                    },
                                )
                            }
                        }
                    }
                }
            )
        },
        sheetContent = {
            if (uiState is DetailsUiState.Success) {
                val manga = (uiState as DetailsUiState.Success).manga
                ChaptersSheetContent(
                    chapters = manga.chapters ?: emptyList(),
                    history = history,
                    bookmarks = bookmarks,
                    pagesState = pagesState,
                    onLoadPages = { viewModel.loadPagesPreview() },
                    onChapterClick = { chapter -> onChapterClick(manga.id, chapter.id) },
                    onBookmarkClick = { bm -> onBookmarkClick(bm.manga.id, bm.chapterId, bm.page) },
                    onPageClick = { chapterId, page -> onPageClick(manga.id, chapterId, page) },
                    onDownloadClick = { showDownloadDialog = true },
                    onForget = { viewModel.removeFromHistory() },
                    onViewPageImage = { url -> fullScreenCover = url },
                    downloadedIds = downloadedChapterIds,
                    onDownloadChapter = { chapter -> viewModel.downloadChapter(chapter) },
                    source = manga.source,
                    onDownloadChapters = { ids -> viewModel.downloadChapters(ids) },
                    onMarkChaptersRead = { ids -> viewModel.markChaptersRead(ids) },
                    onDeleteChapters = { ids -> viewModel.deleteChapters(ids) },
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp))
            }
        },
        sheetPeekHeight = 90.dp, // Adjusted to fit just the toolbar
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) { paddingValues ->
        when (val state = uiState) {
            is DetailsUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is DetailsUiState.Error -> ErrorState(
                error = state.exception,
                onRetry = { viewModel.retry() },
                modifier = Modifier.padding(paddingValues),
                onResolveCloudFlare = { onResolveCloudFlare(it.url) },
            )
            is DetailsUiState.Success -> {
                val favoriteText = if (!isFavorite) {
                    stringResource(Res.string.add_to_favourites)
                } else {
                    val customCategoryNames = allCategories.filter { mangaCategories.contains(it.id) }.map { it.title }
                    // Default (id 0) is a real category now — membership is purely "is 0 in the set".
                    val hasDefault = mangaCategories.contains(0L)
                    
                    val names = buildList {
                        if (hasDefault) add(stringResource(Res.string.default_category))
                        addAll(customCategoryNames)
                    }
                    
                    if (names.isEmpty()) stringResource(Res.string.default_category) else names.joinToString(", ")
                }
                
                MangaDetailsContent(
                    manga = state.manga,
                    isFavorite = isFavorite,
                    favoriteText = favoriteText,
                    onFavoriteClick = {
                        if (allCategories.isEmpty()) {
                            viewModel.toggleCategory(0L, !isFavorite)
                        } else {
                            showCategoryDialog = true
                        }
                    },
                    relatedManga = relatedManga,
                    onRelatedClick = onRelatedClick,
                    readingTimeText = readingTime?.let { formatReadingTime(it) },
                    onCoverClick = { fullScreenCover = it },
                    onTagClick = { tagDialogFor = it },
                    onAuthorClick = { authorDialogFor = it },
                    progressPercent = history?.percent?.takeIf { it >= 0f },
                    currentChapterNumber = history?.let { h ->
                        (uiState as? DetailsUiState.Success)?.manga?.chapters
                            ?.indexOfFirst { it.id == h.chapterId }?.takeIf { it >= 0 }?.plus(1)
                    },
                    scrobblingInfo = scrobblingInfo,
                    onScrobblingClick = { editingScrobblingInfo = it },
                    paddingValues = paddingValues
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaDetailsContent(
    manga: Manga,
    isFavorite: Boolean,
    favoriteText: String,
    onFavoriteClick: () -> Unit,
    relatedManga: List<Manga> = emptyList(),
    onRelatedClick: (mangaId: Long) -> Unit = {},
    readingTimeText: String? = null,
    onCoverClick: (String?) -> Unit = {},
    onTagClick: (MangaTag) -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    progressPercent: Float? = null,
    currentChapterNumber: Int? = null,
    scrobblingInfo: List<org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingInfo> = emptyList(),
    onScrobblingClick: (org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingInfo) -> Unit = {},
    paddingValues: PaddingValues
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .aspectRatio(13f / 18f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // Tap cover -> fullscreen zoomable viewer (Doki ImageActivity).
                    .clickable { onCoverClick(manga.largeCoverUrl ?: manga.coverUrl) }
            ) {
                val coverCtx = coil3.compose.LocalPlatformContext.current
                val coverModel = androidx.compose.runtime.remember(manga.largeCoverUrl, manga.coverUrl, manga.source) {
                    coil3.request.ImageRequest.Builder(coverCtx)
                        .data(manga.largeCoverUrl ?: manga.coverUrl)
                        .apply { mangaSourceExtra(manga.source) }
                        .build()
                }
                // Cover load can fail on CloudFlare/DoH sources; show a Refresh button to re-request
                // (Doki: failed cover/thumbnail is retryable), keyed so the tap re-fetches the same url.
                var coverRetry by remember(coverModel) { mutableIntStateOf(0) }
                key(coverRetry) {
                    SubcomposeAsyncImage(
                        model = coverModel,
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(onClick = { coverRetry++ }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.retry))
                                }
                            }
                        },
                    )
                }
                // NSFW Badges
                if (manga.contentRating == org.nekosukuriputo.nekuva.parsers.model.ContentRating.SUGGESTIVE) {
                    Badge(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp), containerColor = androidx.compose.ui.graphics.Color(0xFFF57C00)) { Text("16+") }
                } else if (manga.contentRating == org.nekosukuriputo.nekuva.parsers.model.ContentRating.ADULT) {
                    Badge(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp), containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F)) { Text("18+") }
                }
            }

            Column(
                modifier = Modifier.weight(0.65f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = manga.title, 
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = manga.authors.joinToString().takeIf { it.isNotEmpty() }
                if (subtitle != null) {
                    Text(
                        text = subtitle, 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                OutlinedButton(
                    onClick = onFavoriteClick,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    val icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(favoriteText)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Details Table
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val parserSource = org.nekosukuriputo.nekuva.parsers.model.MangaParserSource.entries
                    .find { it.name == manga.source.name }
                // Source: favicon + title (Doki source row).
                DetailRowContent(stringResource(Res.string.source)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (parserSource != null) {
                            org.nekosukuriputo.nekuva.core.ui.components.SourceFaviconImage(
                                sourceName = manga.source.name,
                                displayName = parserSource.title,
                                modifier = Modifier.size(20.dp),
                                letterSize = 11.sp,
                            )
                        }
                        Text(parserSource?.title ?: manga.source.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Author: blue + clickable -> author search dialog (Doki showAuthorDialog).
                val author = manga.authors.joinToString().takeIf { it.isNotEmpty() }
                if (author != null) {
                    DetailRowContent(stringResource(Res.string.author)) {
                        Text(
                            text = author,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { onAuthorClick(author) },
                        )
                    }
                }
                // Translation: flag + language name (Doki locale row).
                val locale = parserSource?.locale
                if (!locale.isNullOrBlank()) {
                    DetailRow(stringResource(Res.string.translation), translationLabel(locale))
                }
                if (manga.rating > 0f) {
                    DetailRow(stringResource(Res.string.rating), "${(manga.rating * 10f).toInt() / 10f} / 10")
                }
                manga.state?.let { DetailRow(stringResource(Res.string.state), mangaStateTitle(it)) }
                // Chapters: "Bab X dari Y (reading time)" (Doki) — X = current read position from history.
                val total = manga.chapters?.size ?: 0
                val chaptersValue = buildString {
                    if (currentChapterNumber != null && total > 0) {
                        append(stringResource(Res.string.chapter_d_of_d, currentChapterNumber, total))
                    } else {
                        append(total.toString())
                    }
                    if (readingTimeText != null) append(" ($readingTimeText)")
                }
                DetailRow(stringResource(Res.string.chapters), chaptersValue)
                // Progress: bar + percent (Doki progress row).
                if (progressPercent != null && progressPercent >= 0f) {
                    DetailRowContent(stringResource(Res.string.progress)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { progressPercent.coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f),
                            )
                            Text("${(progressPercent * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Tracking cards (Doki recyclerViewScrobbling): per-service status; tap to edit/unlink.
        org.nekosukuriputo.nekuva.details.ui.scrobbling.ScrobblingInfoCards(
            infos = scrobblingInfo,
            onClick = onScrobblingClick,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Description
        val description = manga.description?.replace("<br>".toRegex(RegexOption.IGNORE_CASE), "\n")?.replace("<[^>]*>".toRegex(), "")?.trim()
        if (!description.isNullOrEmpty()) {
            // Doki `description_collapse`: when collapsing is off, show the full synopsis by default.
            val descSettings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
            var expanded by remember { mutableStateOf(descSettings.isDescriptionExpanded) }
            Column(modifier = Modifier.padding(horizontal = 16.dp).animateContentSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(Res.string.description), style = MaterialTheme.typography.titleMedium)
                    if (!expanded) {
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(Res.string.more))
                        }
                    }
                }
                Text(
                    text = description, 
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Genre/tag chips (Doki chipsTags): compact, tightly packed; tapping opens the tag search dialog.
        if (!manga.tags.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                manga.tags!!.forEach { tag ->
                    SuggestionChip(
                        onClick = { onTagClick(tag) },
                        label = { Text(tag.title, style = MaterialTheme.typography.labelLarge) },
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }
        
        // Related manga (Doki RelatedMangaUseCase): horizontal row of covers, tap → that manga's details.
        if (relatedManga.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.related_manga),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(relatedManga, key = { it.id }) { related ->
                        RelatedMangaItem(manga = related, onClick = { onRelatedClick(related.id) })
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Detail row with an arbitrary value slot (icons, clickable text, progress bar). */
@Composable
fun DetailRowContent(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(0.65f)) { content() }
    }
}

/** Source locale → flag emoji + native language name (Doki translation indicator). */
private fun translationLabel(locale: String): String {
    val lang = locale.substringBefore('-').substringBefore('_').lowercase()
    val flag = when (lang) {
        "id", "in" -> "🇮🇩"; "en" -> "🇬🇧"; "ja" -> "🇯🇵"; "ko" -> "🇰🇷"; "zh" -> "🇨🇳"
        "ru" -> "🇷🇺"; "fr" -> "🇫🇷"; "es" -> "🇪🇸"; "de" -> "🇩🇪"; "pt" -> "🇵🇹"
        "ar" -> "🇸🇦"; "vi" -> "🇻🇳"; "th" -> "🇹🇭"; "tr" -> "🇹🇷"; "it" -> "🇮🇹"; "pl" -> "🇵🇱"
        else -> "🏳"
    }
    val name = runCatching {
        java.util.Locale.forLanguageTag(lang).getDisplayLanguage(java.util.Locale.forLanguageTag(lang))
            .replaceFirstChar { it.uppercase() }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: lang.uppercase()
    return "$flag $name"
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Per-manga reading stats (Doki MangaStatsSheet, simplified): total read time + pages, on demand. */
@Composable
private fun MangaStatsDialog(mangaId: Long, onDismiss: () -> Unit) {
    val statsRepository = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.stats.data.StatsRepository>()
    var info by remember { mutableStateOf<org.nekosukuriputo.nekuva.stats.domain.MangaStatsInfo?>(null) }
    LaunchedEffect(mangaId) {
        info = runCatching { statsRepository.getMangaStats(mangaId) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.statistics)) },
        text = {
            val data = info
            if (data == null || (data.totalDurationMs == 0L && data.totalPages == 0)) {
                Text(stringResource(Res.string.nothing_found))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val totalMin = (data.totalDurationMs / 60_000L).toInt()
                    DetailRow(stringResource(Res.string.reading_time), formatReadingTime(ReadingTimeInfo(totalMin / 60, totalMin % 60, false)))
                    DetailRow(stringResource(Res.string.pages), data.totalPages.toString())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** A related-manga cover + title in the Details horizontal row (Doki related). */
@Composable
private fun RelatedMangaItem(manga: Manga, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(13f / 18f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val relCtx = coil3.compose.LocalPlatformContext.current
            val relModel = androidx.compose.runtime.remember(manga.coverUrl, manga.source) {
                coil3.request.ImageRequest.Builder(relCtx)
                    .data(manga.coverUrl)
                    .apply { mangaSourceExtra(manga.source) }
                    .build()
            }
            AsyncImage(
                model = relModel,
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Format [ReadingTimeInfo] compactly using the short string resources (Doki ReadingTime.formatShort). */
@Composable
private fun formatReadingTime(info: ReadingTimeInfo): String = when {
    info.hours == 0 && info.minutes == 0 -> stringResource(Res.string.less_than_minute)
    info.hours == 0 -> stringResource(Res.string.minutes_short, info.minutes)
    info.minutes == 0 -> stringResource(Res.string.hours_short, info.hours)
    else -> stringResource(Res.string.hours_minutes_short, info.hours, info.minutes)
}

private enum class SheetView { CHAPTERS, PAGES, BOOKMARKS }

@Composable
fun ChaptersSheetContent(
    chapters: List<MangaChapter>,
    history: org.nekosukuriputo.nekuva.core.model.MangaHistory?,
    bookmarks: List<Bookmark>,
    pagesState: PagesPreviewState,
    onLoadPages: () -> Unit,
    onChapterClick: (MangaChapter) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onPageClick: (chapterId: Long, page: Int) -> Unit,
    onDownloadClick: () -> Unit,
    onForget: () -> Unit,
    onViewPageImage: (String) -> Unit = {},
    downloadedIds: Set<Long> = emptySet(),
    onDownloadChapter: (MangaChapter) -> Unit = {},
    source: org.nekosukuriputo.nekuva.parsers.model.MangaSource? = null,
    // Chapter multi-select batch actions (Doki ChaptersFragment ActionMode).
    onDownloadChapters: (Set<Long>) -> Unit = {},
    onMarkChaptersRead: (Set<Long>) -> Unit = {},
    onDeleteChapters: (Set<Long>) -> Unit = {},
) {
    val tabSettings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
    val pagesEnabled = tabSettings.isPagesTabEnabled
    // Doki `details_tab`: default section — 0/last=chapters, 1=pages (if enabled), 2=bookmarks.
    var view by remember {
        mutableStateOf(
            when (tabSettings.defaultDetailsTab) {
                2 -> SheetView.BOOKMARKS
                1 -> if (pagesEnabled) SheetView.PAGES else SheetView.CHAPTERS
                else -> SheetView.CHAPTERS
            }
        )
    }
    // Load the page previews lazily the first time the Pages section is shown.
    androidx.compose.runtime.LaunchedEffect(view) { if (view == SheetView.PAGES) onLoadPages() }
    var readMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar (Drag handle is provided by BottomSheetScaffold automatically)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Chapters view
                IconButton(onClick = { view = SheetView.CHAPTERS }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = stringResource(Res.string.chapters),
                        tint = if (view == SheetView.CHAPTERS) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                // Pages preview (Doki pages_tab) — shown only when enabled in Appearance settings.
                if (pagesEnabled) {
                    IconButton(onClick = { view = SheetView.PAGES }) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = stringResource(Res.string.pages),
                            tint = if (view == SheetView.PAGES) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                // Bookmarks of this manga
                IconButton(onClick = { view = SheetView.BOOKMARKS }) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(Res.string.bookmarks),
                        tint = if (view == SheetView.BOOKMARKS) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }

            // Doki-style split button: main = read/continue + a connected trailing segment opening the popup.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Button(
                    onClick = {
                        if (history != null) {
                            val chapter = chapters.find { it.id == history.chapterId }
                            if (chapter != null) onChapterClick(chapter)
                        } else if (chapters.isNotEmpty()) {
                            onChapterClick(chapters.first())
                        }
                    },
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    val buttonText = if (history != null) {
                        val chapter = chapters.find { it.id == history.chapterId }
                        val chapterTitle = chapter?.title?.takeIf { it.isNotEmpty() } ?: chapter?.name
                        if (chapterTitle != null) "${stringResource(Res.string.resume)}: $chapterTitle" else stringResource(Res.string.read)
                    } else {
                        stringResource(Res.string.read)
                    }
                    Text(buttonText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    Button(
                        onClick = { readMenuExpanded = true },
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.more_options),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(expanded = readMenuExpanded, onDismissRequest = { readMenuExpanded = false }) {
                        // Incognito: deferred (own area) — present but disabled.
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.incognito_mode)) },
                            enabled = false,
                            onClick = {},
                        )
                        if (history != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.remove_from_history)) },
                                onClick = {
                                    readMenuExpanded = false
                                    onForget()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.download)) },
                            onClick = {
                                readMenuExpanded = false
                                onDownloadClick()
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        when (view) {
            SheetView.CHAPTERS -> {
                ChaptersTab(
                    chapters = chapters,
                    downloadedIds = downloadedIds,
                    historyChapterId = history?.chapterId,
                    onChapterClick = onChapterClick,
                    onDownloadChapter = onDownloadChapter,
                    onDownloadChapters = onDownloadChapters,
                    onMarkChaptersRead = onMarkChaptersRead,
                    onDeleteChapters = onDeleteChapters,
                )
            }
            SheetView.PAGES -> {
                when (val ps = pagesState) {
                    is PagesPreviewState.Success -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItemsIndexed(ps.pages) { index, page ->
                                DetailsPageThumb(
                                    url = page.preview?.takeIf { it.isNotEmpty() } ?: page.url,
                                    number = index + 1,
                                    onClick = { onPageClick(ps.chapterId, index) },
                                    // Long-press a page -> fullscreen image viewer (zoom + save + share).
                                    onLongClick = { onViewPageImage(page.url) },
                                    source = source,
                                )
                            }
                        }
                    }
                    is PagesPreviewState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error)
                    }
                    PagesPreviewState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.nothing_here), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }
            SheetView.BOOKMARKS -> {
                if (bookmarks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.no_bookmarks_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(bookmarks, key = { it.pageId }) { bm ->
                            DetailsBookmarkThumb(bookmark = bm, onClick = { onBookmarkClick(bm) }, source = source)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chapters list with Doki's `opt_chapters` toolbar (search, reverse, downloaded-only filter, grid view) and
 * an ActionMode-style multi-select (long-press → select → batch download / mark read / delete). The current
 * (last-read) chapter is highlighted. All view state is local to the sheet, matching Doki.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChaptersTab(
    chapters: List<MangaChapter>,
    downloadedIds: Set<Long>,
    historyChapterId: Long?,
    onChapterClick: (MangaChapter) -> Unit,
    onDownloadChapter: (MangaChapter) -> Unit,
    onDownloadChapters: (Set<Long>) -> Unit,
    onMarkChaptersRead: (Set<Long>) -> Unit,
    onDeleteChapters: (Set<Long>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var reversed by remember { mutableStateOf(false) }
    var downloadedOnly by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<Long>()) }

    val displayed = remember(chapters, query, reversed, downloadedOnly, downloadedIds) {
        chapters
            .let { list -> if (downloadedOnly) list.filter { it.id in downloadedIds } else list }
            .let { list ->
                if (query.isBlank()) list
                else list.filter { (it.title?.takeIf { t -> t.isNotEmpty() } ?: it.name ?: "").contains(query, ignoreCase = true) }
            }
            .let { list -> if (reversed) list.asReversed() else list }
    }

    fun toggle(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selection.isEmpty()) {
            // Normal toolbar (Doki opt_chapters): search, reverse, downloaded-only, grid view.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchOpen) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(Res.string.search_chapters)) },
                        trailingIcon = {
                            IconButton(onClick = { query = ""; searchOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                            }
                        },
                    )
                } else {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_chapters))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { reversed = !reversed }) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = stringResource(Res.string.reverse),
                            tint = if (reversed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { downloadedOnly = !downloadedOnly }) {
                        Icon(
                            Icons.Filled.SdCard,
                            contentDescription = stringResource(Res.string.on_device),
                            tint = if (downloadedOnly) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { gridView = !gridView }) {
                        Icon(
                            if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = stringResource(Res.string.chapters_grid_view),
                        )
                    }
                }
            }
        } else {
            // Selection action bar (Doki ActionMode): count + select-all, download, mark read, delete.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selection = emptySet() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                }
                Text(
                    text = selection.size.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Select range (Doki action_select_range): fill in every chapter between the first and last selected.
                IconButton(
                    enabled = selection.size >= 2 || (selection.size == 1),
                    onClick = {
                        val indices = displayed.withIndex().filter { it.value.id in selection }.map { it.index }
                        val lo = indices.minOrNull()
                        val hi = indices.maxOrNull()
                        if (lo != null && hi != null && hi > lo) {
                            selection = displayed.subList(lo, hi + 1).mapTo(HashSet()) { it.id }
                        }
                    },
                ) {
                    Icon(Icons.Default.UnfoldMore, contentDescription = stringResource(Res.string.select_range))
                }
                IconButton(onClick = { selection = displayed.mapTo(HashSet()) { it.id } }) {
                    Icon(Icons.Default.SelectAll, contentDescription = stringResource(Res.string.select_all))
                }
                IconButton(onClick = { onDownloadChapters(selection); selection = emptySet() }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(Res.string.download))
                }
                IconButton(onClick = { onMarkChaptersRead(selection); selection = emptySet() }) {
                    Icon(Icons.Default.DoneAll, contentDescription = stringResource(Res.string.mark_as_current))
                }
                // Delete only the downloaded chapters in the selection.
                val deletable = selection.intersect(downloadedIds)
                if (deletable.isNotEmpty()) {
                    IconButton(onClick = { onDeleteChapters(deletable); selection = emptySet() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.delete))
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (gridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(displayed, key = { it.id }) { chapter ->
                        ChapterGridCell(
                            chapter = chapter,
                            isDownloaded = chapter.id in downloadedIds,
                            isCurrent = chapter.id == historyChapterId,
                            selected = chapter.id in selection,
                            onClick = { if (selection.isNotEmpty()) toggle(chapter.id) else onChapterClick(chapter) },
                            onLongClick = { toggle(chapter.id) },
                        )
                    }
                }
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(displayed, key = { it.id }) { chapter ->
                        ChapterItem(
                            chapter = chapter,
                            onClick = { if (selection.isNotEmpty()) toggle(chapter.id) else onChapterClick(chapter) },
                            isDownloaded = chapter.id in downloadedIds,
                            onDownload = { onDownloadChapter(chapter) },
                            onLongClick = { toggle(chapter.id) },
                            selected = chapter.id in selection,
                            selectionMode = selection.isNotEmpty(),
                            isCurrent = chapter.id == historyChapterId,
                        )
                    }
                }
                org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

/** Compact chapter cell for the chapters grid view (Doki chapters_grid_view). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChapterGridCell(
    chapter: MangaChapter,
    isDownloaded: Boolean,
    isCurrent: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Chapter number if present, else the (trimmed) title.
        val label = chapter.number.takeIf { it > 0f }?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
            ?: (chapter.title?.takeIf { it.isNotEmpty() } ?: chapter.name ?: "?")
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
        )
        if (isDownloaded) {
            Icon(
                Icons.Filled.SdCard,
                contentDescription = stringResource(Res.string.on_device),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).size(14.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DetailsPageThumb(
    url: String?,
    number: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    source: org.nekosukuriputo.nekuva.parsers.model.MangaSource? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(13f / 18f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val thumbCtx = coil3.compose.LocalPlatformContext.current
        val thumbModel = androidx.compose.runtime.remember(url, source) {
            coil3.request.ImageRequest.Builder(thumbCtx)
                .data(url)
                .apply { if (source != null) mangaSourceExtra(source) }
                .build()
        }
        AsyncImage(
            model = thumbModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailsBookmarkThumb(
    bookmark: Bookmark,
    onClick: () -> Unit,
    source: org.nekosukuriputo.nekuva.parsers.model.MangaSource? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(13f / 18f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        // Thumbnail = the actual bookmarked page image (like Doki).
        val bmCtx = coil3.compose.LocalPlatformContext.current
        val bmModel = androidx.compose.runtime.remember(bookmark.imageUrl, source) {
            coil3.request.ImageRequest.Builder(bmCtx)
                .data(bookmark.imageUrl)
                .apply { if (source != null) mangaSourceExtra(source) }
                .build()
        }
        AsyncImage(
            model = bmModel,
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
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChapterItem(
    chapter: MangaChapter,
    onClick: () -> Unit,
    isDownloaded: Boolean = false,
    onDownload: () -> Unit = {},
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
    selectionMode: Boolean = false,
    isCurrent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            val chapterTitle = chapter.title?.takeIf { it.isNotEmpty() } ?: chapter.name
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Doki highlights the current (last-read) chapter in the accent colour.
                color = if (isCurrent) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
            )
            if (chapter.uploadDate > 0L) {
                Text(text = org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo(chapter.uploadDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (selectionMode) {
            // In selection mode the per-row download/badge is replaced by the checkbox above.
            if (isDownloaded) {
                Icon(Icons.Filled.SdCard, contentDescription = stringResource(Res.string.on_device), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            }
        } else if (isDownloaded) {
            // Doki: a downloaded chapter shows an SD-card badge instead of the download button.
            Icon(
                Icons.Filled.SdCard,
                contentDescription = stringResource(Res.string.on_device),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            IconButton(onClick = onDownload) {
                Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(Res.string.download))
            }
        }
    }
}

@Composable
fun CategorySelectionDialog(
    categories: List<org.nekosukuriputo.nekuva.core.model.FavouriteCategory>,
    selectedCategories: Set<Long>,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleCategory: (Long, Boolean) -> Unit,
    onManageClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_to_favourites)) },
        text = {
            LazyColumn {
                item {
                    // Default is category id 0 — checked iff the manga is actually in category 0.
                    val isDefaultSelected = selectedCategories.contains(0L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCategory(0L, !isDefaultSelected) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isDefaultSelected, onCheckedChange = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(Res.string.default_category))
                    }
                }
                items(categories) { category ->
                    val isSelected = selectedCategories.contains(category.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCategory(category.id, !isSelected) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                        Spacer(Modifier.width(16.dp))
                        Text(category.title)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_done)) // Or "OK"
            }
        },
        dismissButton = {
            TextButton(onClick = onManageClick) {
                Text(stringResource(Res.string.manage))
            }
        }
    )
}
