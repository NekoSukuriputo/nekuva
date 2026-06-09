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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Scaffold
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
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.local.ui.MangaGridItem
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.ContentType
import org.nekosukuriputo.nekuva.parsers.model.MangaState
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteListScreen(
    viewModel: RemoteListViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var searchActive by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(Res.string.filter),
                                tint = if (filterState.isFilterApplied) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            QuickFilterRow(
                state = filterState,
                searchActive = searchActive,
                onOpenSheet = { showFilterSheet = true },
                onToggleTag = { viewModel.toggleTag(it) },
                onClearSearch = { viewModel.clearSearch() },
                onEditSearch = { searchActive = true },
            )
            Box(modifier = Modifier.fillMaxSize()) {
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
                    is RemoteListUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() })
                    is RemoteListUiState.Success -> {
                        val gridState = rememberLazyGridState()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.mangaList) { manga ->
                                MangaGridItem(manga = manga, onClick = { onMangaClick(manga.id) })
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
}

/** Doki-style quick-filter chip row under the toolbar: Genre entry, active search chip, quick genres. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFilterRow(
    state: FilterUiState,
    searchActive: Boolean,
    onOpenSheet: () -> Unit,
    onToggleTag: (MangaTag) -> Unit,
    onClearSearch: () -> Unit,
    onEditSearch: () -> Unit,
) {
    val quickTags = remember(state.selectedTags, state.availableTags) {
        (state.selectedTags.toList() + state.availableTags.filter { it !in state.selectedTags }).take(12)
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
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
        if (!state.query.isNullOrBlank() && !searchActive) {
            item {
                InputChip(
                    selected = true,
                    onClick = onEditSearch,
                    label = { Text(state.query) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.cancel),
                            modifier = Modifier.size(18.dp).clickable(onClick = onClearSearch),
                        )
                    },
                )
            }
        }
        items(quickTags) { tag ->
            FilterChip(
                selected = tag in state.selectedTags,
                onClick = { onToggleTag(tag) },
                label = { Text(tag.title) },
            )
        }
    }
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

            if (state.isOptionsLoading && state.availableTags.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Genre (include)
            if (state.availableTags.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.genres)) {
                    state.availableTags.sortedBy { it.title }.forEach { tag ->
                        FilterChip(
                            selected = tag in state.selectedTags,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag.title) },
                        )
                    }
                }
            }

            // Kecualikan genre (exclude) — only if the source supports it
            if (state.isTagsExclusionSupported && state.availableTags.isNotEmpty()) {
                FilterSection(title = stringResource(Res.string.genres_exclude)) {
                    state.availableTags.sortedBy { it.title }.forEach { tag ->
                        FilterChip(
                            selected = tag in state.selectedTagsExclude,
                            onClick = { viewModel.toggleTagExclude(tag) },
                            label = { Text(tag.title) },
                        )
                    }
                }
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

            Spacer(Modifier.height(8.dp))
            // Bottom bar mirrors Doki: "Simpan" (save preset) + "Selesai" (close — apply is already live).
            // Save-as-named-preset (SavedFilters) is DEFERRED, so "Simpan" is shown but disabled (no fake action).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { /* Deferred: SavedFilters preset — see MIGRATION.md */ },
                    enabled = false,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
