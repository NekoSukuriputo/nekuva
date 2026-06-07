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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
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
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.mangaList) { manga ->
                            MangaGridItem(
                                manga = manga,
                                onClick = { onMangaClick(manga.id) }
                            )
                        }
                        if (state.isAppending) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (state.hasNextPage) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                                LaunchedEffect(Unit) {
                                    viewModel.loadNextPage()
                                }
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
