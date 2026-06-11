package org.nekosukuriputo.nekuva.explore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.model.MangaSourceInfo
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
	viewModel: ExploreViewModel = koinViewModel(),
	onSourceClick: (String) -> Unit,
	onSearchSubmit: (String) -> Unit,
	onBookmarksClick: () -> Unit,
	onDownloadsClick: () -> Unit,
	onSettingsClick: () -> Unit,
) {
	val uiState by viewModel.uiState.collectAsState()
	var searchQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

	when (val state = uiState) {
		is ExploreUiState.Loading -> {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				CircularProgressIndicator()
			}
		}
		is ExploreUiState.Empty -> {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Text(stringResource(Res.string.text_empty_holder_primary))
			}
		}
        is ExploreUiState.Success -> {
            val gridState = rememberLazyGridState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { /* DITUNDA - Local Storage Action */ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Icon(Icons.Default.SdStorage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(Res.string.local_storage))
                            }
                            Button(onClick = onBookmarksClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(Res.string.bookmarks))
                            }
                            Button(onClick = { /* DITUNDA */ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(Res.string.random))
                            }
                            Button(onClick = onDownloadsClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(Res.string.downloads))
                            }
                            Button(onClick = onSettingsClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(stringResource(Res.string.settings))
                            }
                        }
                    }

                    items(state.sources) { source ->
                        SourceGridItem(
                            source = source,
                            onClick = {
                                val id = (source.mangaSource as? MangaParserSource)?.name ?: ""
                                onSourceClick(id)
                            }
                        )
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

@Composable
private fun SourceGridItem(
	source: MangaSourceInfo,
	onClick: () -> Unit,
) {
    val iconUrl = "favicon://${source.mangaSource.name}"

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.clickable(onClick = onClick)
			.padding(8.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
        SubcomposeAsyncImage(
            model = iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            error = {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
		Text(
            text = source.mangaSource.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
	}
}
