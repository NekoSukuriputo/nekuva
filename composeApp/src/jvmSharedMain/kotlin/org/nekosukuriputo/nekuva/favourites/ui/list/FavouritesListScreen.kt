package org.nekosukuriputo.nekuva.favourites.ui.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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

    Scaffold { paddingValues ->
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
                    onClick = { onMangaClick(it.id) },
                    progressOf = { deco.progressOf(it) },
                    badgesOf = { MangaBadges(favourite = showFavBadge) },
                )
            }
        }
    }
}
