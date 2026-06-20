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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.bookmarks.domain.Bookmark
import org.nekosukuriputo.nekuva.core.model.isLocal
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
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
    onOpenManga: (mangaId: Long) -> Unit = {},
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

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showMangaStats by remember { mutableStateOf(false) }
    var showEditOverride by remember { mutableStateOf(false) }
    var fullScreenCover by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val downloadStartedMsg = stringResource(Res.string.download_started)
    val downloadAddedMsg = stringResource(Res.string.download_added)
    val detailsLabel = stringResource(Res.string.details)

    val editOverrideState = uiState
    if (showEditOverride && editOverrideState is DetailsUiState.Success) {
        EditOverrideDialog(
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
            is DetailsUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(paddingValues))
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
                AsyncImage(
                    model = manga.largeCoverUrl ?: manga.coverUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                DetailRow(stringResource(Res.string.source), manga.source.name)
                DetailRow(stringResource(Res.string.author), manga.authors.joinToString().takeIf { it.isNotEmpty() } ?: "-")
                // Fallback for locale, if not present we just use "-" or name
                val localeStr = try { manga.source.javaClass.getMethod("getLang").invoke(manga.source) as String } catch(e: Exception) { "-" }
                DetailRow(stringResource(Res.string.translation), if (localeStr != "-") localeStr else "-")
                
                if (manga.rating >= 0f) {
                    DetailRow(stringResource(Res.string.rating), "${manga.rating} / 10")
                }
                DetailRow(stringResource(Res.string.state), manga.state?.name ?: "-")
                DetailRow(stringResource(Res.string.chapters), manga.chapters?.size?.toString() ?: "0")
                // Estimated reading time (Doki ReadingTimeUseCase) — only when enabled & long enough.
                if (readingTimeText != null) {
                    DetailRow(stringResource(Res.string.reading_time), readingTimeText)
                }
            }
        }

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
        
        // Tags
        if (!manga.tags.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                manga.tags!!.forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { /* Deferred: search tag */ },
                        label = { Text(tag.title) },
                        shape = RoundedCornerShape(16.dp)
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

/**
 * Edit custom title / cover (Doki OverrideConfig, CORE-7). Cover is set by URL (cross-platform);
 * a blank field reverts that part to the source value. File-picker / "pick page" deferred (MIGRATION.md).
 */
@Composable
private fun EditOverrideDialog(
    currentTitle: String,
    currentCoverUrl: String?,
    onDismiss: () -> Unit,
    onSave: (title: String?, coverUrl: String?) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }
    var coverUrl by remember { mutableStateOf(currentCoverUrl.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Cover preview + URL field (Doki change_cover; here via URL instead of file picker).
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(13f / 18f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                OutlinedTextField(
                    value = coverUrl,
                    onValueChange = { coverUrl = it },
                    label = { Text(stringResource(Res.string.change_cover)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (coverUrl.isNotEmpty()) {
                            TextButton(onClick = { coverUrl = "" }) { Text(stringResource(Res.string.reset)) }
                        }
                    },
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.manga_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, coverUrl) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
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
            AsyncImage(
                model = manga.coverUrl,
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
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(chapters) { chapter ->
                            ChapterItem(chapter = chapter, onClick = { onChapterClick(chapter) })
                        }
                    }
                    org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
                        state = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
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
                            DetailsBookmarkThumb(bookmark = bm, onClick = { onBookmarkClick(bm) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DetailsPageThumb(url: String?, number: Int, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(13f / 18f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = url,
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
private fun DetailsBookmarkThumb(bookmark: Bookmark, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(13f / 18f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        // Thumbnail = the actual bookmarked page image (like Doki).
        AsyncImage(
            model = bookmark.imageUrl,
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

@Composable
fun ChapterItem(chapter: MangaChapter, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            val chapterTitle = chapter.title?.takeIf { it.isNotEmpty() } ?: chapter.name
            Text(text = chapterTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chapter.uploadDate > 0L) {
                Text(text = org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo(chapter.uploadDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        IconButton(onClick = { /* Deferred: Download chapter */ }) {
            Icon(Icons.Outlined.FileDownload, contentDescription = "Download")
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
