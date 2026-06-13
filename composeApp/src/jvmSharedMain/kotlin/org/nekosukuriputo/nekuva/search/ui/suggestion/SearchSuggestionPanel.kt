package org.nekosukuriputo.nekuva.search.ui.suggestion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.clear_history
import nekuva.composeapp.generated.resources.remove
import nekuva.composeapp.generated.resources.search_manga
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.parsers.model.Manga
import org.nekosukuriputo.nekuva.parsers.model.MangaParserSource

/**
 * As-you-type suggestion panel under the main search bar (Doki's search suggestion list):
 * tag chips, manga thumbnails, recent queries (deletable + clear all), sources (with enable
 * switch), authors.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSuggestionPanel(
    viewModel: SearchSuggestionViewModel,
    onQueryClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onMangaClick: (Long) -> Unit,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.suggestion.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (state.tags.isNotEmpty()) {
            item(key = "tags") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { onTagClick(tag.title) },
                            label = { Text(tag.title) },
                        )
                    }
                }
            }
        }
        if (state.manga.isNotEmpty()) {
            item(key = "manga") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.manga, key = { it.id }) { manga ->
                        SuggestionMangaItem(manga = manga, onClick = { onMangaClick(manga.id) })
                    }
                }
            }
        }
        items(state.recentQueries, key = { "q:$it" }) { query ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQueryClick(query) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(query, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { viewModel.deleteQuery(query) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.recentQueries.isNotEmpty()) {
            item(key = "clear") {
                TextButton(
                    onClick = { viewModel.clearSearchHistory() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(Res.string.clear_history))
                }
            }
        }
        items(state.sources, key = { "s:${it.source.name}" }) { suggestion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSourceClick(suggestion.source.name) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                AsyncImage(
                    model = "favicon://${suggestion.source.name}",
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    (suggestion.source as? MangaParserSource)?.title ?: suggestion.source.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = suggestion.isEnabled,
                    onCheckedChange = { viewModel.onSourceToggle(suggestion.source, it) },
                )
            }
        }
        items(state.authors, key = { "a:$it" }) { author ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQueryClick(author) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.isEmpty) {
            item(key = "hint") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(Res.string.search_manga),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionMangaItem(manga: Manga, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = manga.coverUrl,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(13f / 18f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Text(
            manga.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}
