package org.nekosukuriputo.nekuva.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ListMode
import org.nekosukuriputo.nekuva.core.prefs.ProgressIndicatorMode
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaGridItem
import org.nekosukuriputo.nekuva.core.ui.components.mangaGridCells
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo
import org.nekosukuriputo.nekuva.core.util.ext.relativeDateKey
import org.nekosukuriputo.nekuva.history.domain.model.MangaWithHistory
import org.nekosukuriputo.nekuva.list.domain.ListFilterOption
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onMangaClick: (Long) -> Unit,
    onResumeClick: (Long, Long) -> Unit,
    onStatsClick: () -> Unit = {},
    viewModel: HistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings, AppSettings.KEY_LIST_MODE_HISTORY)
    val gridSize = rememberGridSize(settings)
    val progressMode = remember { settings.progressIndicatorMode }
    // Sort + grouping (Doki opt_history): persisted sort order + "group by date" toggle.
    val sortOrder by viewModel.sortOrder.collectAsState()
    var grouping by remember { mutableStateOf(settings.isHistoryGroupingEnabled) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var showFavDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // Quick-filter chips (Doki HistoryListQuickFilter): Downloaded/New/Completed/Favorite/Not-favorite/NSFW
    // + popular sources + tags (loaded by the VM).
    val appliedFilters by viewModel.filters.collectAsState()
    val availableFilters by viewModel.availableFilters.collectAsState()
    // Multi-select (Doki ActionMode / mode_history): long-press enters, tap toggles while active.
    val selection = org.nekosukuriputo.nekuva.core.ui.selection.rememberSelectionState<Long>()
    val successList = (uiState as? HistoryUiState.Success)?.list.orEmpty()
    fun selectedMangas() = successList.filter { selection.isSelected(it.manga.id) }.map { it.manga }
    // Pagination (CORE-8): load the next page when scrolled near the end (VM no-ops if the last page was full).
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(gridState) {
        androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 4) viewModel.loadMore() }
    }
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 4) viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            if (selection.isActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selection.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                    },
                    title = { Text(selection.count.toString()) },
                    actions = {
                        IconButton(onClick = { selection.selectAll(successList.map { it.manga.id }) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(Res.string.select_all))
                        }
                        IconButton(onClick = { org.nekosukuriputo.nekuva.core.share.shareMangas(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                        }
                        // Save = download whole manga (Doki action_save).
                        IconButton(onClick = { viewModel.downloadManga(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.Download, contentDescription = stringResource(Res.string.save))
                        }
                        // Add to favourites (Doki action_favourite) -> category picker.
                        IconButton(onClick = { showFavDialog = true }) {
                            Icon(Icons.Filled.FavoriteBorder, contentDescription = stringResource(Res.string.add_to_favourites))
                        }
                        IconButton(onClick = { viewModel.markAsRead(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.DoneAll, contentDescription = stringResource(Res.string.mark_as_completed))
                        }
                        // Auto-fix selected (Doki action_fix): migrate broken/selected to the best alternative.
                        IconButton(onClick = { viewModel.autoFix(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(Res.string.fix))
                        }
                        // Edit override (Doki action_edit_override) — single selection only.
                        if (selection.count == 1) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.edit))
                            }
                        }
                        IconButton(onClick = { viewModel.removeHistory(selectedMangas()); selection.clear() }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.remove))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(Res.string.history)) },
                    actions = {
                        // Display config (Doki action_list_mode): list mode + grid size + grouping + sort.
                        IconButton(onClick = { showConfigSheet = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(Res.string.list_mode))
                        }
                        HistoryOverflowMenu(
                            statsEnabled = settings.isStatsEnabled,
                            onClear = { option ->
                                when (option) {
                                    HistoryClearOption.LAST_2_HOURS ->
                                        viewModel.clearHistoryAfter(System.currentTimeMillis() - 2L * 60 * 60 * 1000)
                                    HistoryClearOption.TODAY ->
                                        viewModel.clearHistoryAfter(startOfTodayMillis())
                                    HistoryClearOption.NOT_FAVORITE -> viewModel.removeNotFavorite()
                                    HistoryClearOption.ALL -> viewModel.clearAllHistory()
                                }
                            },
                            onStats = onStatsClick,
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is HistoryUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is HistoryUiState.Error -> ErrorState(error = state.error, onRetry = { }, modifier = Modifier.padding(paddingValues))
            is HistoryUiState.Success -> {
              Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Incognito banner (Doki InfoModel): reading progress isn't recorded while incognito is on.
                if (settings.isIncognitoModeEnabled) {
                    IncognitoBanner()
                }
                // Quick-filter chips scroll above the list (Doki adds them as the first list item).
                if (state.list.isNotEmpty() || appliedFilters.isNotEmpty()) {
                    HistoryQuickFilterRow(
                        available = availableFilters,
                        applied = appliedFilters,
                        onToggle = { viewModel.toggleFilter(it) },
                    )
                }
                if (state.list.isEmpty()) {
                    EmptyState(
                        message = stringResource(
                            if (appliedFilters.isEmpty()) Res.string.text_history_holder_primary else Res.string.nothing_found,
                        ),
                        secondary = stringResource(
                            if (appliedFilters.isEmpty()) Res.string.text_history_holder_secondary else Res.string.text_empty_holder_secondary_filtered,
                        ),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    // Date grouping (Doki KEY_HISTORY_GROUPING) only for date-based sorts; headers read
                    // "Hari ini"/"Kemarin"/"N hari lalu" then the absolute date ("24 Mei 2026"). Otherwise flat.
                    val dateGrouped = grouping && sortOrder in setOf(
                        org.nekosukuriputo.nekuva.list.domain.ListSortOrder.LAST_READ,
                        org.nekosukuriputo.nekuva.list.domain.ListSortOrder.LONG_AGO_READ,
                        org.nekosukuriputo.nekuva.list.domain.ListSortOrder.NEWEST,
                        org.nekosukuriputo.nekuva.list.domain.ListSortOrder.OLDEST,
                    )
                    val grouped = if (dateGrouped) state.list.groupBy { relativeDateKey(it.history.updatedAt) } else mapOf("" to state.list)
                    fun progressOf(item: MangaWithHistory): Float? =
                        if (progressMode != ProgressIndicatorMode.NONE && item.history.percent >= 0f) item.history.percent else null

                    if (listMode == ListMode.GRID) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = mangaGridCells(gridSize),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            grouped.forEach { (_, items) ->
                                if (dateGrouped) item(span = { GridItemSpan(maxLineSpan) }) { DateHeader(items.first().history.updatedAt) }
                                gridItems(items, key = { it.manga.id }) { item ->
                                    MangaGridItem(
                                        manga = item.manga,
                                        onClick = { if (selection.isActive) selection.toggle(item.manga.id) else onMangaClick(item.manga.id) },
                                        onLongClick = { selection.toggle(item.manga.id) },
                                        progress = progressOf(item),
                                        selected = selection.isSelected(item.manga.id),
                                    )
                                }
                            }
                        }
                    } else {
                        // LIST vs DETAILED_LIST (Doki item_manga_list / item_manga_list_details): same row
                        // composable, `detailed` adds the bigger cover + author/tags lines.
                        val detailed = listMode == ListMode.DETAILED_LIST
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            grouped.forEach { (_, items) ->
                                if (dateGrouped) item { DateHeader(items.first().history.updatedAt) }
                                items(items, key = { it.manga.id }) { item ->
                                    org.nekosukuriputo.nekuva.core.ui.components.MangaListRow(
                                        manga = item.manga,
                                        onClick = { if (selection.isActive) selection.toggle(item.manga.id) else onMangaClick(item.manga.id) },
                                        onLongClick = { selection.toggle(item.manga.id) },
                                        detailed = detailed,
                                        progress = progressOf(item),
                                        selected = selection.isSelected(item.manga.id),
                                    )
                                }
                            }
                        }
                    }
                }
              }
            }
        }
    }

    if (showConfigSheet) {
        val groupingAvailable = sortOrder in setOf(
            org.nekosukuriputo.nekuva.list.domain.ListSortOrder.LAST_READ,
            org.nekosukuriputo.nekuva.list.domain.ListSortOrder.LONG_AGO_READ,
            org.nekosukuriputo.nekuva.list.domain.ListSortOrder.NEWEST,
            org.nekosukuriputo.nekuva.list.domain.ListSortOrder.OLDEST,
        )
        org.nekosukuriputo.nekuva.core.ui.components.ListConfigSheet(
            listMode = listMode,
            onListModeChange = { settings.historyListMode = it },
            gridSize = gridSize,
            onGridSizeChange = { settings.gridSize = it },
            sortOrders = org.nekosukuriputo.nekuva.list.domain.ListSortOrder.HISTORY.toList(),
            currentSort = sortOrder,
            onSortChange = { viewModel.setSortOrder(it) },
            groupingSupported = true,
            groupingEnabled = grouping,
            groupingAvailable = groupingAvailable,
            onGroupingChange = { settings.isHistoryGroupingEnabled = it; grouping = it },
            onDismiss = { showConfigSheet = false },
        )
    }

    if (showFavDialog) {
        val categories by viewModel.favouriteCategories.collectAsState()
        val toAdd = selectedMangas()
        AlertDialog(
            onDismissRequest = { showFavDialog = false },
            title = { Text(stringResource(Res.string.add_to_favourites)) },
            text = {
                Column {
                    // Default category (id 0) + user categories (Doki favourite picker).
                    val rows = buildList {
                        add(0L to stringResource(Res.string.default_category))
                        categories.forEach { add(it.id to it.title) }
                    }
                    rows.forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.addToFavourites(id, toAdd); showFavDialog = false; selection.clear()
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFavDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showEditDialog) {
        val target = selectedMangas().firstOrNull()
        if (target != null) {
            org.nekosukuriputo.nekuva.core.ui.components.EditOverrideDialog(
                currentTitle = target.title,
                currentCoverUrl = target.coverUrl,
                onDismiss = { showEditDialog = false },
                onSave = { title, coverUrl ->
                    viewModel.setOverride(target, title, coverUrl)
                    showEditDialog = false
                    selection.clear()
                },
            )
        } else {
            showEditDialog = false
        }
    }
}

/** Doki opt_history clear-history options (Last 2h / Today / Not in favorites / All). */
enum class HistoryClearOption { LAST_2_HOURS, TODAY, NOT_FAVORITE, ALL }

/** Epoch millis at the start of the local day (Doki LocalDate.now().atStartOfDay). */
private fun startOfTodayMillis(): Long =
    java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryOverflowMenu(
    statsEnabled: Boolean,
    onClear: (HistoryClearOption) -> Unit,
    onStats: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.clear_history)) },
                onClick = { expanded = false; showClearDialog = true },
            )
            if (statsEnabled) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.statistics)) },
                    onClick = { expanded = false; onStats() },
                )
            }
        }
    }
    if (showClearDialog) {
        val options = listOf(
            HistoryClearOption.LAST_2_HOURS to Res.string.last_2_hours,
            HistoryClearOption.TODAY to Res.string.today,
            HistoryClearOption.NOT_FAVORITE to Res.string.not_in_favorites,
            HistoryClearOption.ALL to Res.string.clear_all_history,
        )
        var selected by remember { mutableStateOf(HistoryClearOption.LAST_2_HOURS) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(Res.string.clear_history)) },
            text = {
                Column {
                    options.forEach { (option, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { selected = option }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == option, onClick = { selected = option })
                            Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClear(selected) }) {
                    Text(stringResource(Res.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

/** Banner shown while incognito mode is on (Doki InfoModel) — reading progress isn't recorded. */
@Composable
private fun IncognitoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(Res.string.incognito_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(Res.string.incognito_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryQuickFilterRow(
    available: List<ListFilterOption>,
    applied: Set<ListFilterOption>,
    onToggle: (ListFilterOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        available.forEach { option ->
            FilterChip(
                selected = option in applied,
                onClick = { onToggle(option) },
                label = { Text(filterLabel(option)) },
            )
        }
    }
}

@Composable
private fun filterLabel(option: ListFilterOption): String = when (option) {
    ListFilterOption.Downloaded -> stringResource(Res.string.downloaded)
    ListFilterOption.Macro.NEW_CHAPTERS -> stringResource(Res.string.new_chapters)
    ListFilterOption.Macro.COMPLETED -> stringResource(Res.string.status_completed)
    ListFilterOption.Macro.FAVORITE -> stringResource(Res.string.favourites)
    ListFilterOption.Macro.NSFW -> stringResource(Res.string.nsfw)
    is ListFilterOption.Tag -> option.tag.title
    is ListFilterOption.Source -> option.titleText?.toString() ?: option.mangaSource.name
    is ListFilterOption.Inverted -> when (option.option) {
        ListFilterOption.Macro.FAVORITE -> stringResource(Res.string.not_in_favorites)
        ListFilterOption.Macro.NSFW -> stringResource(Res.string.sfw)
        else -> option.titleText?.toString() ?: ""
    }
    else -> option.titleText?.toString() ?: ""
}

@Composable
private fun DateHeader(epochMillis: Long) {
    Text(
        text = calculateTimeAgo(epochMillis),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

