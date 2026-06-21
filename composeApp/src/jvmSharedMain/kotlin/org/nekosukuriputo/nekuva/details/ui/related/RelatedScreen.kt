package org.nekosukuriputo.nekuva.details.ui.related

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaListContent
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.find_similar
import nekuva.composeapp.generated.resources.nothing_found

/** Doki "Find similar" screen (RelatedListFragment): related-manga grid for the seed manga. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedScreen(
    viewModel: RelatedViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)
    val gridSize = rememberGridSize(settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.find_similar)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cancel))
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is RelatedUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is RelatedUiState.Empty -> EmptyState(message = stringResource(Res.string.nothing_found), modifier = Modifier.padding(padding))
            is RelatedUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.retry() }, modifier = Modifier.padding(padding))
            is RelatedUiState.Success -> MangaListContent(
                mangas = state.mangaList,
                listMode = listMode,
                gridSize = gridSize,
                onClick = { onMangaClick(it.id) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
