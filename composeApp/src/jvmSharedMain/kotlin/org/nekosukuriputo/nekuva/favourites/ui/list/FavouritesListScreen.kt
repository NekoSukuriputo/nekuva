package org.nekosukuriputo.nekuva.favourites.ui.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesListScreen(
    categoryId: Long = 0L,
    onMangaClick: (Long) -> Unit
) {
    val viewModel: FavouritesListViewModel = koinViewModel(parameters = { parametersOf(categoryId) })
    val uiState by viewModel.uiState.collectAsState()
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings, AppSettings.KEY_LIST_MODE_FAVORITES)
    val gridSize = rememberGridSize(settings)
    val showFavBadge = (settings.getMangaListBadges() and 1) != 0 // everything here is a favourite
    // Favourites are all favourited (badge handled above), so only borrow the progress indicator.
    val deco = rememberMangaListDecorations(includeFavouriteBadge = false)
    // Multi-select (Doki mode_favourites): long-press enters; contextual bar select-all/share/mark/remove.
    val selection = org.nekosukuriputo.nekuva.core.ui.selection.rememberSelectionState()
    val mangas = (uiState as? FavouritesUiState.Success)?.mangas.orEmpty()
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
                        IconButton(onClick = { viewModel.markAsRead(selected()); selection.clear() }) {
                            Icon(Icons.Filled.DoneAll, contentDescription = stringResource(Res.string.mark_as_completed))
                        }
                        IconButton(onClick = { viewModel.removeFromFavourites(selection.selected); selection.clear() }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.remove))
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is FavouritesUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is FavouritesUiState.Empty -> EmptyState(message = stringResource(Res.string.text_empty_holder_primary), modifier = Modifier.padding(paddingValues))
            is FavouritesUiState.Error -> ErrorState(error = state.exception, onRetry = { }, modifier = Modifier.padding(paddingValues))
            is FavouritesUiState.Success -> {
                MangaListContent(
                    mangas = state.mangas,
                    listMode = listMode,
                    gridSize = gridSize,
                    modifier = Modifier.padding(paddingValues),
                    onClick = { if (selection.isActive) selection.toggle(it.id) else onMangaClick(it.id) },
                    onLongClick = { selection.toggle(it.id) },
                    progressOf = { deco.progressOf(it) },
                    badgesOf = { MangaBadges(favourite = showFavBadge) },
                    selectedIds = selection.selected,
                )
            }
        }
    }
}
