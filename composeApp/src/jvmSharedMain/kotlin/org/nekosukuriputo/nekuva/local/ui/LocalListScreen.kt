package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.ErrorState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaBadges
import org.nekosukuriputo.nekuva.core.ui.components.MangaListContent
import org.nekosukuriputo.nekuva.core.ui.components.rememberGridSize
import org.nekosukuriputo.nekuva.core.ui.components.rememberMangaListMode
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@Composable
fun LocalListScreen(
    viewModel: LocalListViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var mangaToDelete by remember { mutableStateOf<Manga?>(null) }
    val settings = koinInject<AppSettings>()
    val listMode = rememberMangaListMode(settings)          // Local follows the global list mode
    val gridSize = rememberGridSize(settings)
    val showSavedBadge = (settings.getMangaListBadges() and 2) != 0 // downloaded == "saved"

    Scaffold { paddingValues ->
        when (val state = uiState) {
            is LocalListUiState.Loading -> LoadingState(modifier = Modifier.padding(paddingValues))
            is LocalListUiState.Empty -> EmptyState(message = stringResource(Res.string.nothing_here), modifier = Modifier.padding(paddingValues))
            is LocalListUiState.Error -> ErrorState(error = state.exception, onRetry = { viewModel.loadManga() }, modifier = Modifier.padding(paddingValues))
            is LocalListUiState.Success -> {
                MangaListContent(
                    mangas = state.mangaList,
                    listMode = listMode,
                    gridSize = gridSize,
                    modifier = Modifier.padding(paddingValues),
                    onClick = { onMangaClick(it.id) },
                    onLongClick = { mangaToDelete = it },
                    badgesOf = { MangaBadges(saved = showSavedBadge) },
                )
            }
        }
    }

    mangaToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { mangaToDelete = null },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text(target.title) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteManga(target)
                    mangaToDelete = null
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { mangaToDelete = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
