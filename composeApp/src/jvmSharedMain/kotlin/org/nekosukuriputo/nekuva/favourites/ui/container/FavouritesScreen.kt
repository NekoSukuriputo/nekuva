package org.nekosukuriputo.nekuva.favourites.ui.container

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import org.nekosukuriputo.nekuva.favourites.ui.list.FavouritesListScreen
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.favourites
import nekuva.composeapp.generated.resources.all_favourites
import nekuva.composeapp.generated.resources.default_category
import nekuva.composeapp.generated.resources.sort_order

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    onMangaClick: (Long) -> Unit,
    onManageCategoriesClick: () -> Unit
) {
    val viewModel = koinViewModel<FavouritesViewModel>()
    val categories by viewModel.categories.collectAsState()
    val isAllFavouritesVisible by viewModel.isAllFavouritesVisible.collectAsState()

    val displayCategories = buildList {
        if (isAllFavouritesVisible) {
            add(FavouriteCategory(-1L, stringResource(Res.string.all_favourites), -1, 0L, true, true))
        }
        add(FavouriteCategory(0L, "Default", 0, 0L, true, true)) // Default category representation, we can refine this later
        addAll(categories)
    }

    val pagerState = rememberPagerState(pageCount = { displayCategories.size })
    val coroutineScope = rememberCoroutineScope()

    // Global favourites sort (Doki KEY_FAVORITES_ORDER) — applies to all category tabs; pages observe it live.
    val settings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
    var favSort by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(settings.allFavoritesSortOrder) }
    var showSortDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.favourites)) },
                actions = {
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(Res.string.sort_order))
                    }
                    IconButton(onClick = onManageCategoriesClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Categories")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (displayCategories.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 8.dp
                ) {
                    displayCategories.forEachIndexed { index, category ->
                        val title = when (category.id) {
                            -1L -> stringResource(Res.string.all_favourites)
                            0L -> stringResource(Res.string.default_category)
                            else -> category.title
                        }
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) }
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val categoryId = displayCategories[page].id
                // When categoryId is -1L, it should show ALL favorites. 
                // We'll pass -1L to FavouritesListScreen, but we need to ensure FavouritesListViewModel handles it correctly!
                FavouritesListScreen(
                    categoryId = categoryId,
                    onMangaClick = onMangaClick
                )
            }
        }
    }

    if (showSortDialog) {
        org.nekosukuriputo.nekuva.core.ui.components.SortOrderDialog(
            current = favSort,
            options = org.nekosukuriputo.nekuva.list.domain.ListSortOrder.FAVORITES,
            onSelect = { settings.allFavoritesSortOrder = it; favSort = it },
            onDismiss = { showSortDialog = false },
        )
    }
}
