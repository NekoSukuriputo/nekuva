package org.nekosukuriputo.nekuva.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
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
	onLocalClick: () -> Unit,
	onManageSources: () -> Unit,
	onOpenManga: (Long) -> Unit,
	onSourceSettings: (String) -> Unit,
) {
	val uiState by viewModel.uiState.collectAsState()
	val isRandomLoading by viewModel.isRandomLoading.collectAsState()
	val selected by viewModel.selected.collectAsState()
	// Incognito banner (Doki): shown while incognito mode is on — reading progress isn't recorded.
	val settings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
	val incognitoOn by settings.observeBoolean(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_INCOGNITO_MODE, false)
		.collectAsState(initial = settings.isIncognitoModeEnabled)

	when (val state = uiState) {
		is ExploreUiState.Loading -> {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				CircularProgressIndicator()
			}
		}
		is ExploreUiState.Empty -> {
			// No sources enabled (e.g. a fresh install): guide the user to the source catalog to add some,
			// instead of a blank screen (Doki no_manga_sources_text + a centred "Sources catalog" CTA).
			Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Icon(
						imageVector = Icons.Default.TravelExplore,
						contentDescription = null,
						modifier = Modifier.size(72.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = stringResource(Res.string.no_manga_sources_text),
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
					)
					Button(onClick = onManageSources) {
						Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
						Spacer(Modifier.size(8.dp))
						Text(stringResource(Res.string.sources_catalog))
					}
				}
			}
		}
        is ExploreUiState.Success -> {
            val gridState = rememberLazyGridState()
            val selectionActive = selected.isNotEmpty()
            // Back clears the selection first (Doki ActionMode behaviour).
            org.nekosukuriputo.nekuva.core.ui.PlatformBackHandler(enabled = selectionActive) { viewModel.clearSelection() }
          Column(modifier = Modifier.fillMaxSize()) {
            if (selectionActive) {
                val selectedSources = state.sources.filter { it.mangaSource.name in selected }
                SourceSelectionBar(
                    count = selected.size,
                    allPinned = selectedSources.all { it.isPinned },
                    singleSelection = selected.size == 1,
                    onClose = { viewModel.clearSelection() },
                    onPin = { viewModel.pinSelected(selectedSources.any { !it.isPinned }) },
                    onDisable = { viewModel.disableSelected() },
                    onShortcut = {
                        selectedSources.singleOrNull()?.let { s ->
                            val title = (s.mangaSource as? MangaParserSource)?.title ?: s.mangaSource.name
                            org.nekosukuriputo.nekuva.core.shortcuts.pinSourceShortcut(s.mangaSource.name, title)
                        }
                        viewModel.clearSelection()
                    },
                    onSettings = {
                        selectedSources.singleOrNull()?.let { onSourceSettings(it.mangaSource.name) }
                        viewModel.clearSelection()
                    },
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (incognitoOn) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            org.nekosukuriputo.nekuva.core.ui.components.IncognitoBanner()
                        }
                    }
                    // Doki ExploreButtons: exactly four shortcuts (Local, Bookmarks, Random, Downloads) in a 2×2 grid.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExploreButton(Icons.Default.SdStorage, stringResource(Res.string.local_storage), Modifier.weight(1f), onClick = onLocalClick)
                                ExploreButton(Icons.Default.Bookmark, stringResource(Res.string.bookmarks), Modifier.weight(1f), onClick = onBookmarksClick)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExploreButton(
                                    Icons.Default.Casino, stringResource(Res.string.random), Modifier.weight(1f),
                                    loading = isRandomLoading,
                                    onClick = { viewModel.openRandom(onOpenManga) },
                                )
                                ExploreButton(Icons.Default.Download, stringResource(Res.string.downloads), Modifier.weight(1f), onClick = onDownloadsClick)
                            }
                        }
                    }

                    // Doki "Remote sources" header (ListHeader + Catalog button).
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.remote_sources),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            androidx.compose.material3.TextButton(onClick = onManageSources) {
                                Text(stringResource(Res.string.catalog))
                            }
                        }
                    }

                    items(state.sources) { source ->
                        SourceGridItem(
                            source = source,
                            selected = source.mangaSource.name in selected,
                            onClick = {
                                if (selectionActive) {
                                    viewModel.toggleSelection(source)
                                } else {
                                    onSourceClick((source.mangaSource as? MangaParserSource)?.name ?: "")
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(source) },
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
}

/** Doki ExploreButton: an equal-width tonal shortcut (icon + label); shows a spinner while [loading]. */
@Composable
private fun ExploreButton(
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	label: String,
	modifier: Modifier = Modifier,
	loading: Boolean = false,
	onClick: () -> Unit,
) {
	Button(
		onClick = onClick,
		modifier = modifier,
		enabled = !loading,
		colors = ButtonDefaults.buttonColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant,
			contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
		),
	) {
		if (loading) {
			CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
		} else {
			Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
		}
		Spacer(modifier = Modifier.size(8.dp))
		Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}

/** Contextual selection bar (Doki mode_source ActionMode): pin/unpin, disable, settings (single). */
@Composable
private fun SourceSelectionBar(
	count: Int,
	allPinned: Boolean,
	singleSelection: Boolean,
	onClose: () -> Unit,
	onPin: () -> Unit,
	onDisable: () -> Unit,
	onShortcut: () -> Unit,
	onSettings: () -> Unit,
) {
	androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			androidx.compose.material3.IconButton(onClick = onClose) {
				Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
			}
			Text(count.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
			androidx.compose.material3.IconButton(onClick = onPin) {
				Icon(
					Icons.Default.PushPin,
					contentDescription = stringResource(if (allPinned) Res.string.unpin else Res.string.pin),
					tint = if (allPinned) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
				)
			}
			androidx.compose.material3.IconButton(onClick = onDisable) {
				Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(Res.string.disable))
			}
			if (singleSelection) {
				androidx.compose.material3.IconButton(onClick = onShortcut) {
					Icon(Icons.Default.AddToHomeScreen, contentDescription = stringResource(Res.string.create_shortcut))
				}
				androidx.compose.material3.IconButton(onClick = onSettings) {
					Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
				}
			}
		}
	}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SourceGridItem(
	source: MangaSourceInfo,
	onClick: () -> Unit,
	onLongClick: () -> Unit = {},
	selected: Boolean = false,
) {
    val displayTitle = (source.mangaSource as? MangaParserSource)?.title ?: source.mangaSource.name

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
			.combinedClickable(onClick = onClick, onLongClick = onLongClick)
			.padding(8.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
        Box(contentAlignment = Alignment.TopEnd) {
            org.nekosukuriputo.nekuva.core.ui.components.SourceFaviconImage(
                sourceName = source.mangaSource.name,
                displayName = displayTitle,
                modifier = Modifier.size(72.dp),
                letterSize = 34.sp,
            )
            // Pinned indicator (Doki) — toggled via long-press.
            if (source.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
		Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
	}
}
