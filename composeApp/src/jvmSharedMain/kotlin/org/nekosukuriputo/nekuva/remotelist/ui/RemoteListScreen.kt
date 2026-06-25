package org.nekosukuriputo.nekuva.remotelist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.horizontalWheelScroll
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ListMode
import org.nekosukuriputo.nekuva.core.ui.components.MangaGridItem
import org.nekosukuriputo.nekuva.core.ui.components.MangaListRow
import org.nekosukuriputo.nekuva.core.ui.components.mangaGridCells
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.YEAR_MIN
import org.nekosukuriputo.nekuva.parsers.model.YEAR_UNKNOWN
import java.util.Calendar
import java.util.Locale
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteListScreen(
    viewModel: RemoteListViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onResolveCloudFlare: (url: String) -> Unit = {},
    onSourceSettings: (sourceName: String) -> Unit = {},
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRandomLoading by viewModel.isRandomLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Appearance: list mode + grid size (Doki — remote browse uses the global list mode).
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)
    val gridSize = rememberGridSize(settings)
    val deco = org.nekosukuriputo.nekuva.list.ui.rememberMangaListDecorations() // progress + favourite badge

    var searchActive by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showListConfig by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // "Open random" (dice) resolves a manga off-screen → navigate to its details when ready.
    LaunchedEffect(Unit) {
        viewModel.openManga.collect { onMangaClick(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        val focusRequester = remember { FocusRequester() }
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            singleLine = true,
                            placeholder = { Text(stringResource(Res.string.search)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.submitSearch()
                                searchActive = false
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    } else {
                        Text(viewModel.sourceId)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            if (filterState.query.isNullOrBlank()) viewModel.clearSearch()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (searchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                            }
                        }
                    } else {
                        // Doki source app bar: search (if supported) + dice (random) + overflow menu.
                        if (viewModel.isSearchSupported) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
                            }
                        }
                        // Refresh (Doki): reload the list — the alternative to pull-to-refresh on Desktop.
                        IconButton(onClick = { viewModel.refresh() }, enabled = !isRefreshing) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.try_again))
                        }
                        IconButton(onClick = { viewModel.openRandom() }, enabled = !isRandomLoading) {
                            Icon(Icons.Default.Casino, contentDescription = stringResource(Res.string.random))
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.settings),
                                tint = if (filterState.isFilterApplied) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            // Saring (filter)
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.filter)) },
                                onClick = { menuExpanded = false; showFilterSheet = true },
                            )
                            // Reset filter — only when a filter is applied (Doki action_filter_reset).
                            if (filterState.isFilterApplied) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.reset_filter)) },
                                    onClick = { menuExpanded = false; viewModel.resetFilter() },
                                )
                            }
                            // Opsi daftar (list options)
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.list_options)) },
                                onClick = { menuExpanded = false; showListConfig = true },
                            )
                            // Pengaturan (per-source settings)
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.settings)) },
                                onClick = { menuExpanded = false; onSourceSettings(viewModel.sourceId) },
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Doki: the quick-filter chip row is gated by the `quick_filter` setting (filter stays
            // reachable via the toolbar filter icon).
            if (settings.isQuickFilterEnabled) {
                QuickFilterRow(
                    state = filterState,
                    viewModel = viewModel,
                    searchActive = searchActive,
                    onOpenSheet = { showFilterSheet = true },
                    onEditSearch = { searchActive = true },
                )
            }
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = uiState) {
                    is RemoteListUiState.Loading -> LoadingState()
                    is RemoteListUiState.Empty -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EmptyState(message = stringResource(Res.string.nothing_found))
                            if (state.canResetFilter) {
                                TextButton(onClick = { viewModel.resetFilter() }) {
                                    Text(stringResource(Res.string.reset_filter))
                                }
                            }
                        }
                    }
                    is RemoteListUiState.Error -> {
                        // CloudFlare wall → "solve captcha" button (embedded browser) then retry; else plain retry.
                        ErrorState(
                            error = state.exception,
                            onRetry = { viewModel.retry() },
                            onResolveCloudFlare = { onResolveCloudFlare(it.url) },
                        )
                    }
                    is RemoteListUiState.Success -> {
                        if (listMode == ListMode.GRID) {
                            val gridState = rememberLazyGridState()
                            LazyVerticalGrid(
                                columns = mangaGridCells(gridSize),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.mangaList) { manga ->
                                    MangaGridItem(
                                        manga = manga,
                                        onClick = { onMangaClick(manga.id) },
                                        progress = deco.progressOf(manga),
                                        badges = deco.badgesOf(manga),
                                    )
                                }
                                if (state.isAppending) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                } else if (state.hasNextPage) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LaunchedEffect(state.mangaList.size) { viewModel.loadNextPage() }
                                    }
                                }
                            }
                            org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
                                state = gridState,
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            )
                        } else {
                            val detailed = listMode == ListMode.DETAILED_LIST
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            androidx.compose.foundation.lazy.LazyColumn(
                                contentPadding = PaddingValues(vertical = 4.dp),
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.mangaList) { manga ->
                                    MangaListRow(
                                        manga = manga,
                                        onClick = { onMangaClick(manga.id) },
                                        detailed = detailed,
                                        progress = deco.progressOf(manga),
                                        badges = deco.badgesOf(manga),
                                    )
                                }
                                if (state.isAppending) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                } else if (state.hasNextPage) {
                                    item {
                                        LaunchedEffect(state.mangaList.size) { viewModel.loadNextPage() }
                                    }
                                }
                            }
                            org.nekosukuriputo.nekuva.core.ui.components.FastScrollbar(
                                state = listState,
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            state = filterState,
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showListConfig) {
        // "Opsi daftar" — remote browse uses the global list mode (Doki), like the rest of the app.
        org.nekosukuriputo.nekuva.list.ui.ListConfigSheet(
            settings = settings,
            listModeKey = AppSettings.KEY_LIST_MODE,
            onDismiss = { showListConfig = false },
        )
    }
}

/**
 * Doki-style quick-filter chip row under the toolbar (FilterHeaderProducer parity): Genre entry,
 * active search chip, then a closeable chip for every applied non-genre filter (author, language,
 * type, demographic, rating, state), then quick genre chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFilterRow(
    state: FilterUiState,
    viewModel: RemoteListViewModel,
    searchActive: Boolean,
    onOpenSheet: () -> Unit,
    onEditSearch: () -> Unit,
) {
    val quickTags = remember(state.selectedTags, state.availableTags) {
        (state.selectedTags.toList() + state.availableTags.filter { it !in state.selectedTags }).take(12)
    }
    val chipListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(
        state = chipListState,
        modifier = Modifier.fillMaxWidth()
            .horizontalWheelScroll(chipListState), // Desktop: mouse-wheel scrolls the filter chips
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AssistChip(
                onClick = onOpenSheet,
                label = { Text(stringResource(Res.string.genres)) },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        items(state.savedFilters) { preset ->
            FilterChip(
                selected = preset.id == state.selectedSavedFilterId,
                onClick = { viewModel.toggleSavedFilter(preset.id) },
                label = { Text(preset.name) },
            )
        }
        if (!state.query.isNullOrBlank() && !searchActive) {
            item {
                ActiveFilterChip(
                    label = state.query,
                    onClick = onEditSearch,
                    onClose = { viewModel.clearSearch() },
                    leadingIcon = Icons.Default.Search,
                )
            }
        }
        if (!state.author.isNullOrBlank()) {
            item { ActiveFilterChip(label = state.author, onClose = { viewModel.setAuthor(null) }) }
        }
        state.selectedLocale?.let { locale ->
            item { ActiveFilterChip(label = localeTitle(locale), onClose = { viewModel.setLocale(null) }) }
        }
        state.selectedOriginalLocale?.let { locale ->
            item { ActiveFilterChip(label = localeTitle(locale), onClose = { viewModel.setOriginalLocale(null) }) }
        }
        items(state.selectedTypes.toList()) { type ->
            ActiveFilterChip(label = contentTypeTitle(type), onClose = { viewModel.toggleType(type) })
        }
        items(state.selectedDemographics.toList()) { d ->
            ActiveFilterChip(label = demographicTitle(d), onClose = { viewModel.toggleDemographic(d) })
        }
        items(state.selectedContentRating.toList()) { cr ->
            ActiveFilterChip(label = contentRatingTitle(cr), onClose = { viewModel.toggleContentRating(cr) })
        }
        items(state.selectedStates.toList()) { st ->
            ActiveFilterChip(label = mangaStateTitle(st), onClose = { viewModel.toggleState(st) })
        }
        items(quickTags) { tag ->
            FilterChip(
                selected = tag in state.selectedTags,
                onClick = { viewModel.toggleTag(tag) },
                label = { Text(tag.title) },
            )
        }
    }
}

/** A selected (active) filter shown as a checked chip with a ✕ to remove it — Doki's header chips. */
@Composable
private fun ActiveFilterChip(
    label: String,
    onClose: () -> Unit,
    onClick: () -> Unit = onClose,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    InputChip(
        selected = true,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.remove),
                modifier = Modifier.size(18.dp).clickable(onClick = onClose),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: FilterUiState,
    viewModel: RemoteListViewModel,
    onDismiss: () -> Unit,
) {
    // LIVE: no draft. Every toggle calls the ViewModel directly, which re-queries immediately
    // (mirrors Doki). Closing the sheet keeps the applied filter (no revert), like Doki.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<org.nekosukuriputo.nekuva.filter.data.PersistableFilter?>(null) }
    // null = closed; false = include-mode; true = exclude-mode (Doki TagsCatalogSheet excludeMode).
    var tagsCatalogExcludeMode by remember { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.filter),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.resetFilter() }) { Text(stringResource(Res.string.reset_filter)) }
            }

            // Urutkan (sort) — dropdown
            if (state.availableSortOrders.isNotEmpty()) {
                Text(stringResource(Res.string.sort_order), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = state.sortOrder?.let { sortOrderTitle(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.availableSortOrders.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(sortOrderTitle(order)) },
                                onClick = { viewModel.setSortOrder(order); expanded = false },
                            )
                        }
                    }
                }
            }

            // Filter tersimpan (saved presets) — Doki shows them right after sort.
            if (state.savedFilters.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.saved_filters)) {
                    state.savedFilters.forEach { preset ->
                        var menuOpen by remember(preset.id) { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = preset.id == state.selectedSavedFilterId,
                                onClick = { viewModel.toggleSavedFilter(preset.id) },
                                label = { Text(preset.name) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp).clickable { menuOpen = true },
                                    )
                                },
                            )
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.rename)) },
                                    onClick = { menuOpen = false; renameTarget = preset },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.delete)) },
                                    onClick = { menuOpen = false; viewModel.deleteSavedFilter(preset.id) },
                                )
                            }
                        }
                    }
                }
            }

            if (state.isOptionsLoading && state.availableTags.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Bahasa (locale) — Doki shows it right after sort; null = any.
            if (state.availableLocales.isNotEmpty()) {
                LocaleDropdown(
                    title = stringResource(Res.string.language),
                    locales = state.availableLocales,
                    selected = state.selectedLocale,
                    onSelect = { viewModel.setLocale(it) },
                )
            }

            // Bahasa asli (original locale) — only if the source supports it.
            if (state.isOriginalLocaleSupported && state.availableLocales.isNotEmpty()) {
                LocaleDropdown(
                    title = stringResource(Res.string.original_language),
                    locales = state.availableLocales,
                    selected = state.selectedOriginalLocale,
                    onSelect = { viewModel.setOriginalLocale(it) },
                )
            }

            // Genre (include) — a limited chip set; "Lebih banyak" opens the full searchable catalog
            // (Doki: FilterFieldLayout "more" button → TagsCatalogSheet).
            if (state.availableTags.isNotEmpty()) {
                val inlineTags = remember(state.selectedTags, state.availableTags) {
                    (state.selectedTags.toList() + state.availableTags.sortedBy { it.title }.filter { it !in state.selectedTags })
                        .take(INLINE_TAGS_LIMIT)
                }
                FilterSection(title = stringResource(Res.string.genres)) {
                    inlineTags.forEach { tag ->
                        FilterChip(
                            selected = tag in state.selectedTags,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag.title) },
                        )
                    }
                    if (state.catalogTags.size > inlineTags.size) {
                        AssistChip(
                            onClick = { tagsCatalogExcludeMode = false },
                            label = { Text(stringResource(Res.string.more)) },
                        )
                    }
                }
            }

            // Kecualikan genre (exclude) — only if the source supports it
            if (state.isTagsExclusionSupported && state.availableTags.isNotEmpty()) {
                val inlineExclude = remember(state.selectedTagsExclude, state.availableTags) {
                    (state.selectedTagsExclude.toList() + state.availableTags.sortedBy { it.title }.filter { it !in state.selectedTagsExclude })
                        .take(INLINE_TAGS_LIMIT)
                }
                FilterSection(title = stringResource(Res.string.genres_exclude)) {
                    inlineExclude.forEach { tag ->
                        FilterChip(
                            selected = tag in state.selectedTagsExclude,
                            onClick = { viewModel.toggleTagExclude(tag) },
                            label = { Text(tag.title) },
                        )
                    }
                    if (state.catalogTags.size > inlineExclude.size) {
                        AssistChip(
                            onClick = { tagsCatalogExcludeMode = true },
                            label = { Text(stringResource(Res.string.more)) },
                        )
                    }
                }
            }

            // Penulis (author search) — only if the source supports it. Doki places it after genres.
            if (state.isAuthorSearchSupported) {
                Text(stringResource(Res.string.author), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                var authorText by remember(state.author) { mutableStateOf(state.author.orEmpty()) }
                OutlinedTextField(
                    value = authorText,
                    onValueChange = { authorText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.author)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.setAuthor(authorText) }),
                    trailingIcon = {
                        if (authorText.isNotEmpty()) {
                            IconButton(onClick = { authorText = ""; viewModel.setAuthor(null) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Tipe (content type)
            if (state.availableTypes.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.type)) {
                    state.availableTypes.forEach { type ->
                        FilterChip(
                            selected = type in state.selectedTypes,
                            onClick = { viewModel.toggleType(type) },
                            label = { Text(contentTypeTitle(type)) },
                        )
                    }
                }
            }

            // Status (state)
            if (state.availableStates.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.state)) {
                    state.availableStates.forEach { st ->
                        FilterChip(
                            selected = st in state.selectedStates,
                            onClick = { viewModel.toggleState(st) },
                            label = { Text(mangaStateTitle(st)) },
                        )
                    }
                }
            }

            // Content rating
            if (state.availableContentRating.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.content_rating)) {
                    state.availableContentRating.forEach { cr ->
                        FilterChip(
                            selected = cr in state.selectedContentRating,
                            onClick = { viewModel.toggleContentRating(cr) },
                            label = { Text(contentRatingTitle(cr)) },
                        )
                    }
                }
            }

            // Demografi
            if (state.availableDemographics.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.demographics)) {
                    state.availableDemographics.forEach { d ->
                        FilterChip(
                            selected = d in state.selectedDemographics,
                            onClick = { viewModel.toggleDemographic(d) },
                            label = { Text(demographicTitle(d)) },
                        )
                    }
                }
            }

            // Tahun (single year slider) — dragging to the minimum clears the filter, like Doki.
            if (state.isYearSupported) {
                val maxYear = remember { Calendar.getInstance()[Calendar.YEAR] + 1 }
                var yearValue by remember(state.selectedYear) {
                    mutableStateOf(if (state.selectedYear == YEAR_UNKNOWN) YEAR_MIN else state.selectedYear)
                }
                Text(
                    text = stringResource(Res.string.year) + ": " +
                        if (yearValue <= YEAR_MIN) stringResource(Res.string.any) else yearValue.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = yearValue.toFloat(),
                    valueRange = YEAR_MIN.toFloat()..maxYear.toFloat(),
                    onValueChange = { yearValue = it.toInt() },
                    onValueChangeFinished = {
                        viewModel.setYear(if (yearValue <= YEAR_MIN) YEAR_UNKNOWN else yearValue)
                    },
                )
            }

            // Rentang tahun (range slider) — endpoints at the extremes mean "unbounded", like Doki.
            if (state.isYearRangeSupported) {
                val maxYear = remember { Calendar.getInstance()[Calendar.YEAR] + 1 }
                var range by remember(state.selectedYearFrom, state.selectedYearTo) {
                    val from = if (state.selectedYearFrom == YEAR_UNKNOWN) YEAR_MIN else state.selectedYearFrom
                    val to = if (state.selectedYearTo == YEAR_UNKNOWN) maxYear else state.selectedYearTo
                    mutableStateOf(from.toFloat()..to.toFloat())
                }
                Text(
                    text = stringResource(Res.string.years) + ": " +
                        (if (range.start.toInt() <= YEAR_MIN) stringResource(Res.string.any) else range.start.toInt().toString()) +
                        " – " +
                        (if (range.endInclusive.toInt() >= maxYear) stringResource(Res.string.any) else range.endInclusive.toInt().toString()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                RangeSlider(
                    value = range,
                    valueRange = YEAR_MIN.toFloat()..maxYear.toFloat(),
                    onValueChange = { range = it },
                    onValueChangeFinished = {
                        val from = range.start.toInt().let { if (it <= YEAR_MIN) YEAR_UNKNOWN else it }
                        val to = range.endInclusive.toInt().let { if (it >= maxYear) YEAR_UNKNOWN else it }
                        viewModel.setYearRange(from, to)
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            // Bottom bar mirrors Doki: "Simpan" (save preset, enabled when a filter is applied and
            // not already saved) + "Selesai" (close — apply is already live).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    enabled = state.isFilterApplied && state.selectedSavedFilterId == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.save))
                }
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.done))
                }
            }
        }
    }

    if (showSaveDialog) {
        FilterNameDialog(
            title = stringResource(Res.string.save_filter),
            initialName = "",
            existingNames = state.savedFilters.map { it.name },
            onConfirm = { name ->
                viewModel.saveCurrentFilter(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }
    renameTarget?.let { target ->
        FilterNameDialog(
            title = stringResource(Res.string.rename),
            initialName = target.name,
            existingNames = state.savedFilters.map { it.name } - target.name,
            onConfirm = { name ->
                viewModel.renameSavedFilter(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
    tagsCatalogExcludeMode?.let { excludeMode ->
        TagsCatalogSheet(
            tags = state.catalogTags,
            selected = if (excludeMode) state.selectedTagsExclude else state.selectedTags,
            title = stringResource(if (excludeMode) Res.string.genres_exclude else Res.string.genres),
            onToggle = { tag ->
                if (excludeMode) viewModel.toggleTagExclude(tag) else viewModel.toggleTag(tag)
            },
            onDismiss = { tagsCatalogExcludeMode = null },
        )
    }
}

private const val INLINE_TAGS_LIMIT = 24

/** Full searchable tags catalog (Doki TagsCatalogSheet): search field + checkbox list, live toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagsCatalogSheet(
    tags: List<MangaTag>,
    selected: Set<MangaTag>,
    title: String,
    onToggle: (MangaTag) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(tags, query) {
        if (query.isBlank()) tags else tags.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(filtered, key = { it.key + it.title }) { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(tag) }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(checked = tag in selected, onCheckedChange = { onToggle(tag) })
                        Text(tag.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Name-input dialog for saving/renaming a filter preset; warns before overwriting an existing name. */
@Composable
private fun FilterNameDialog(
    title: String,
    initialName: String,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val collides = trimmed in existingNames
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= org.nekosukuriputo.nekuva.filter.data.PersistableFilter.MAX_TITLE_LENGTH) name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.enter_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (collides) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.filter_overwrite_confirm, trimmed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty(), onClick = { onConfirm(trimmed) }) {
                Text(stringResource(if (collides) Res.string.overwrite else Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/** Dropdown for the language / original-language filter; the first entry (null) means "any". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocaleDropdown(
    title: String,
    locales: List<Locale>,
    selected: Locale?,
    onSelect: (Locale?) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = localeTitle(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf<Locale?>(null) + locales).forEach { locale ->
                DropdownMenuItem(
                    text = { Text(localeTitle(locale)) },
                    onClick = { onSelect(locale); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
