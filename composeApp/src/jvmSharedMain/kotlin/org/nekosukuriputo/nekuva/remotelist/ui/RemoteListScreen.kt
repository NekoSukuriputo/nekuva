package org.nekosukuriputo.nekuva.remotelist.ui

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
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.local.ui.MangaGridItem

@Composable
fun RemoteListScreen(
    viewModel: RemoteListViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        when (val state = uiState) {
            is RemoteListUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is RemoteListUiState.Empty -> EmptyState(message = "No manga found.", modifier = Modifier.padding(paddingValues))
            is RemoteListUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(paddingValues))
            is RemoteListUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) {
                    items(state.mangaList) { manga ->
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
