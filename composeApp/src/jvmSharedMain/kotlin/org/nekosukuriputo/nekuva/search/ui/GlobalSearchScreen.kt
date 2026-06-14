package org.nekosukuriputo.nekuva.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.ui.components.LoadingState
import org.nekosukuriputo.nekuva.core.ui.components.MangaGridItem
import org.nekosukuriputo.nekuva.list.ui.MangaListDecorations
import org.nekosukuriputo.nekuva.list.ui.rememberMangaListDecorations
import nekuva.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel = koinViewModel(),
    onMangaClick: (Long) -> Unit,
    onSourceMore: (sourceId: String, query: String) -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val deco = rememberMangaListDecorations() // favourite badge + reading progress on results

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            uiState.sections.isEmpty() && uiState.isLoading -> LoadingState(modifier = Modifier.padding(paddingValues))
            uiState.sections.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(message = stringResource(Res.string.nothing_found))
                    Text(
                        text = stringResource(Res.string.text_search_holder_secondary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.sections, key = { it.id }) { section ->
                        SearchSectionItem(
                            section = section,
                            deco = deco,
                            onMangaClick = onMangaClick,
                            onMore = { sourceId -> onSourceMore(sourceId, viewModel.query) },
                            onOpenBrowser = onOpenBrowser,
                        )
                    }
                    if (uiState.isLoading) {
                        item(key = "loading-footer") {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (uiState.canSearchDisabled) {
                        // Doki's ButtonFooter: search the sources the user hasn't enabled, on demand.
                        item(key = "search-disabled-footer") {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = { viewModel.continueSearch() }) {
                                    Text(stringResource(Res.string.search_disabled_sources))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionItem(
    section: SearchSection,
    deco: MangaListDecorations,
    onMangaClick: (Long) -> Unit,
    onMore: (sourceId: String) -> Unit,
    onOpenBrowser: (url: String) -> Unit,
) {
    val title = when (section.kind) {
        SearchSectionKind.HISTORY -> stringResource(Res.string.history)
        SearchSectionKind.FAVOURITES -> stringResource(Res.string.favourites)
        SearchSectionKind.LOCAL -> stringResource(Res.string.local_storage)
        SearchSectionKind.SOURCE -> section.sourceName
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // "Lebih"/see-all only for real parser sources (mirrors Doki — UnknownMangaSource has none).
            if (section.kind == SearchSectionKind.SOURCE && section.sourceId != null && section.error == null) {
                TextButton(onClick = { onMore(section.sourceId) }) {
                    Text(stringResource(Res.string.more))
                }
            }
        }
        if (section.error != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = section.error.message ?: stringResource(Res.string.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                // Many source errors are CloudFlare/JS walls (evaluateJs is stubbed) — let the user
                // open the site directly, like Doki's "Open in browser" error action.
                section.browserUrl?.let { url ->
                    TextButton(
                        onClick = { onOpenBrowser(url) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(Res.string.open_in_browser))
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(section.manga, key = { it.id }) { manga ->
                    Box(modifier = Modifier.width(120.dp)) {
                        MangaGridItem(
                            manga = manga,
                            onClick = { onMangaClick(manga.id) },
                            progress = deco.progressOf(manga),
                            badges = deco.badgesOf(manga),
                        )
                    }
                }
            }
        }
    }
}
