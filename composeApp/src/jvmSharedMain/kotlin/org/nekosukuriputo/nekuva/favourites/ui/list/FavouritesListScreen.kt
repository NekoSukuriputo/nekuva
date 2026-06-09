package org.nekosukuriputo.nekuva.favourites.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.local.ui.MangaGridItem
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@Composable
fun FavouritesListScreen(
    categoryId: Long = 0L,
    onMangaClick: (Long) -> Unit
) {
    val viewModel: FavouritesListViewModel = koinViewModel(parameters = { parametersOf(categoryId) })
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        when (val state = uiState) {
            is FavouritesUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is FavouritesUiState.Empty -> EmptyState(message = stringResource(Res.string.text_empty_holder_primary), modifier = Modifier.padding(paddingValues))
            is FavouritesUiState.Error -> ErrorState(error = state.exception, onRetry = { }, modifier = Modifier.padding(paddingValues))
            is FavouritesUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) {
                    items(state.mangas) { manga ->
                        MangaGridItem(
                            manga = manga,
                            onClick = { onMangaClick(manga.id) }
                        )
                    }
                }
            }
        }
    }
}
