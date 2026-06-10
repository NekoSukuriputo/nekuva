package org.nekosukuriputo.nekuva.details.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaChapter
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel = koinViewModel(),
    onChapterClick: (Long, Long) -> Unit,
    onBookmarkClick: (mangaId: Long, chapterId: Long, page: Int) -> Unit,
    onBackClick: () -> Unit,
    onManageCategoriesClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val mangaCategories by viewModel.mangaCategories.collectAsState()
    val history by viewModel.history.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var showCategoryDialog by remember { mutableStateOf(false) }

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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
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
                    IconButton(onClick = { /* Deferred: Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { /* Deferred: Download */ }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Download")
                    }
                    IconButton(onClick = { /* Deferred: Overflow */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
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
                    onChapterClick = { chapter -> onChapterClick(manga.id, chapter.id) },
                    onBookmarkClick = { bm -> onBookmarkClick(bm.manga.id, bm.chapterId, bm.page) }
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
            }
        }

        // Description
        val description = manga.description?.replace("<br>".toRegex(RegexOption.IGNORE_CASE), "\n")?.replace("<[^>]*>".toRegex(), "")?.trim()
        if (!description.isNullOrEmpty()) {
            var expanded by remember { mutableStateOf(false) }
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
        
        // Related Manga (Deferred Placeholder)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(Res.string.related_manga), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { /* Deferred */ }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.show_all))
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

private enum class SheetView { CHAPTERS, BOOKMARKS }

@Composable
fun ChaptersSheetContent(
    chapters: List<MangaChapter>,
    history: org.nekosukuriputo.nekuva.core.model.MangaHistory?,
    bookmarks: List<Bookmark>,
    onChapterClick: (MangaChapter) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
) {
    var view by remember { mutableStateOf(SheetView.CHAPTERS) }

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
                IconButton(onClick = { /* Deferred: grid layout for chapters */ }) {
                    Icon(Icons.Default.GridView, contentDescription = "Grid View")
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

            Button(
                onClick = {
                    if (history != null) {
                        val chapter = chapters.find { it.id == history.chapterId }
                        if (chapter != null) onChapterClick(chapter)
                    } else if (chapters.isNotEmpty()) {
                        onChapterClick(chapters.first())
                    }
                },
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp)
            ) {
                val buttonText = if (history != null) {
                    val chapter = chapters.find { it.id == history.chapterId }
                    val chapterTitle = chapter?.title?.takeIf { it.isNotEmpty() } ?: chapter?.name
                    if (chapterTitle != null) "${stringResource(Res.string.resume)}: $chapterTitle" else stringResource(Res.string.read)
                } else {
                    stringResource(Res.string.read)
                }
                Text(buttonText)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
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
