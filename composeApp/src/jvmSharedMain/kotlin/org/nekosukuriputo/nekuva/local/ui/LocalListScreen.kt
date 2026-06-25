package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nekosukuriputo.nekuva.core.ui.horizontalWheelScroll
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.ui.components.EditOverrideDialog
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaBadges
import org.nekosukuriputo.nekuva.core.ui.components.MangaListContent
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.list.ui.rememberMangaListDecorations
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

/**
 * Local library list (Doki LocalListFragment). No own toolbar in the normal state — the main shell search
 * bar + overflow (Import / List options / Directories) is the toolbar. Long-press enters selection mode
 * (Doki mode_local): Select-all / Share / Edit override (single) / Delete.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LocalListScreen(
    viewModel: LocalListViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val appliedTags by viewModel.appliedTags.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)          // Local follows the global list mode
    val gridSize = rememberGridSize(settings)
    val showSavedBadge = (settings.getMangaListBadges() and 2) != 0 // downloaded == "saved"
    val deco = rememberMangaListDecorations() // reading progress + favourite badge (Doki indicators)
    // Multi-select (Doki mode_local): long-press enters; contextual bar select-all/share/edit/delete.
    val selection = org.nekosukuriputo.nekuva.core.ui.selection.rememberSelectionState<Long>()
    val mangas = (uiState as? LocalListUiState.Success)?.mangaList.orEmpty()
    fun selected() = mangas.filter { selection.isSelected(it.id) }

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
                        IconButton(onClick = { selection.selectAll(mangas.map { it.id }) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(Res.string.select_all))
                        }
                        IconButton(onClick = { org.nekosukuriputo.nekuva.core.share.shareMangas(selected()); selection.clear() }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                        }
                        // Edit override (Doki action_edit_override) — single selection only.
                        if (selection.count == 1) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.edit))
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.delete))
                        }
                    },
                )
            }
            // Non-selection: the shell search toolbar is the single toolbar (Doki parity).
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Quick tag-filter chips (Doki QuickFilter header): tap to filter the local library by genre.
            // Hidden in selection mode; self-hides when there are too few tags to be useful.
            if (!selection.isActive) {
                LocalQuickTagFilter(
                    available = availableTags,
                    applied = appliedTags,
                    onToggle = viewModel::toggleTag,
                )
            }
            when (val state = uiState) {
                is LocalListUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                is LocalListUiState.Empty -> EmptyState(message = stringResource(Res.string.nothing_here), modifier = Modifier.fillMaxSize())
                is LocalListUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.loadManga() }, modifier = Modifier.fillMaxSize())
                is LocalListUiState.Success -> {
                    MangaListContent(
                        mangas = state.mangaList,
                        listMode = listMode,
                        gridSize = gridSize,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { if (selection.isActive) selection.toggle(it.id) else onMangaClick(it.id) },
                        onLongClick = { selection.toggle(it.id) },
                        progressOf = { deco.progressOf(it) },
                        badgesOf = { MangaBadges(saved = showSavedBadge, favourite = deco.badgesOf(it).favourite) },
                        selectedIds = selection.selected,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val toDelete = selected()
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text(toDelete.joinToString("\n") { it.title }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteManga(toDelete)
                    showDeleteConfirm = false
                    selection.clear()
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showEditDialog) {
        val target = selected().firstOrNull()
        if (target != null) {
            EditOverrideDialog(
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

/** Doki QuickFilter maxCount — at most this many tag chips in the inline row. */
private const val MAX_QUICK_FILTER_CHIPS = 16

/**
 * Inline quick-filter chip row for the Local library (Doki `createFilterHeader` / `QuickFilter`). Shows the
 * library's genre tags as toggleable chips — applied tags first (checked), then the rest — each with a tag
 * icon, mirroring Doki's `ListFilterOption.Tag` (icon `ic_tag`). Tapping toggles the include-tag filter.
 */
@Composable
private fun LocalQuickTagFilter(
    available: List<MangaTag>,
    applied: Set<MangaTag>,
    onToggle: (MangaTag) -> Unit,
) {
    // Doki: skip the row when nothing is applied and there are too few tags to filter by.
    if (applied.isEmpty() && available.size < 3) return
    // Applied tags first (checked), then available unchecked ones (Doki order), capped.
    val chips = remember(available, applied) {
        (applied.toList() + available.filterNot { it in applied }).take(MAX_QUICK_FILTER_CHIPS)
    }
    if (chips.isEmpty()) return
    val chipScroll = rememberLazyListState()
    LazyRow(
        state = chipScroll,
        modifier = Modifier.fillMaxWidth()
            .horizontalWheelScroll(chipScroll), // Desktop: mouse-wheel scrolls the chip row
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.key }) { tag ->
            FilterChip(
                selected = tag in applied,
                onClick = { onToggle(tag) },
                label = { Text(tag.title) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}
