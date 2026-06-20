package org.nekosukuriputo.nekuva.favourites.ui.container

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.favourites.ui.list.FavouritesListScreen
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.all_favourites
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.delete
import nekuva.composeapp.generated.resources.edit_category
import nekuva.composeapp.generated.resources.hide
import nekuva.composeapp.generated.resources.manage
import nekuva.composeapp.generated.resources.name
import nekuva.composeapp.generated.resources.save

/** One tab descriptor: [id] = -1 for "All favourites", else a real category. */
private data class FavTab(val id: Long, val title: String, val isAll: Boolean)

/**
 * Favourites container (Doki FavouritesContainerFragment): "All favourites" tab (when visible) + a tab per
 * library-visible category, each long-pressable for Edit / Delete / Hide (Doki popup_fav_tab). No own
 * toolbar — the main shell search bar + overflow ("List options" / "Manage categories") is the toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavouritesScreen(
    onMangaClick: (Long) -> Unit,
    onManageCategoriesClick: () -> Unit,
) {
    val viewModel = koinViewModel<FavouritesViewModel>()
    val categories by viewModel.categories.collectAsState()
    val isAllFavouritesVisible by viewModel.isAllFavouritesVisible.collectAsState()

    val allLabel = stringResource(Res.string.all_favourites)
    val tabs = buildList {
        if (isAllFavouritesVisible) add(FavTab(FavouritesViewModel.ALL_CATEGORY_ID, allLabel, isAll = true))
        categories.forEach { add(FavTab(it.id, it.title, isAll = false)) }
    }
    if (tabs.isEmpty()) {
        // No visible categories and "All" hidden — show the empty all-favourites list as a fallback.
        FavouritesListScreen(categoryId = FavouritesViewModel.ALL_CATEGORY_ID, onMangaClick = onMangaClick)
        return
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    var menuForTab by remember { mutableStateOf<FavTab?>(null) }
    var renameTab by remember { mutableStateOf<FavTab?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex), edgePadding = 8.dp) {
            tabs.forEachIndexed { index, tab ->
                Box {
                    Tab(
                        selected = pagerState.currentPage == index,
                        // Click selects; long-press opens Edit/Delete/Hide (Doki FavouriteTabPopupMenuProvider).
                        // Tab.onClick is a no-op — combinedClickable on the modifier handles both gestures.
                        onClick = {},
                        modifier = Modifier.combinedClickable(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            onLongClick = { menuForTab = tab },
                        ),
                        text = { Text(tab.title) },
                    )
                    FavTabMenu(
                        tab = if (menuForTab?.id == tab.id) tab else null,
                        onDismiss = { menuForTab = null },
                        onEdit = { renameTab = tab },
                        onDelete = { viewModel.deleteCategory(tab.id) },
                        onHide = { viewModel.hide(tab.id) },
                        onManage = { onManageCategoriesClick() },
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            FavouritesListScreen(categoryId = tabs[page].id, onMangaClick = onMangaClick)
        }
    }

    renameTab?.let { tab ->
        RenameCategoryDialog(
            currentTitle = tab.title,
            onDismiss = { renameTab = null },
            onConfirm = { newTitle -> viewModel.renameCategory(tab.id, newTitle); renameTab = null },
        )
    }
}

@Composable
private fun RenameCategoryDialog(currentTitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentTitle) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_category)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.name)) },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun FavTabMenu(
    tab: FavTab?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onManage: () -> Unit,
) {
    DropdownMenu(expanded = tab != null, onDismissRequest = onDismiss) {
        if (tab?.isAll == true) {
            // "All favourites" tab (Doki popup_fav_tab_all): Hide / Manage.
            DropdownMenuItem(text = { Text(stringResource(Res.string.hide)) }, onClick = { onDismiss(); onHide() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.manage)) }, onClick = { onDismiss(); onManage() })
        } else if (tab != null) {
            // A category tab (Doki popup_fav_tab): Edit / Delete / Hide.
            DropdownMenuItem(text = { Text(stringResource(Res.string.edit_category)) }, onClick = { onDismiss(); onEdit() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.delete)) }, onClick = { onDismiss(); onDelete() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.hide)) }, onClick = { onDismiss(); onHide() })
        }
    }
}
