package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.domain.LocalFilterHolder
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.remotelist.ui.contentRatingTitle
import org.nekosukuriputo.nekuva.remotelist.ui.sortOrderTitle
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.content_rating
import nekuva.composeapp.generated.resources.done
import nekuva.composeapp.generated.resources.filter
import nekuva.composeapp.generated.resources.genres
import nekuva.composeapp.generated.resources.genres_exclude
import nekuva.composeapp.generated.resources.more
import nekuva.composeapp.generated.resources.reset_filter
import nekuva.composeapp.generated.resources.sort_order

private const val COLLAPSED_TAGS = 12

/**
 * Local-library filter sheet (Doki local filter): Sort + Genre (include) + Exclude genre + Content rating,
 * limited to what the local source actually supports. Applying writes the selection to [LocalFilterHolder]
 * (which the local list observes) + the sort to settings, then bumps the holder's revision to re-query once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocalFilterSheet(onDismiss: () -> Unit) {
    val repo = koinInject<LocalMangaRepository>()
    val holder = koinInject<LocalFilterHolder>()
    val settings = koinInject<AppSettings>()

    val sortOrders = remember { repo.sortOrders.filter { it != SortOrder.RELEVANCE } }
    var availableTags by remember { mutableStateOf<List<MangaTag>?>(null) }
    var availableRatings by remember { mutableStateOf<List<ContentRating>>(emptyList()) }

    var sort by remember { mutableStateOf(settings.localListOrder) }
    var include by remember { mutableStateOf(holder.tags.value) }
    var exclude by remember { mutableStateOf(holder.tagsExclude.value) }
    var rating by remember { mutableStateOf(holder.contentRating.value) }
    var genreExpanded by remember { mutableStateOf(false) }
    var excludeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val opts = runCatching { repo.getFilterOptions() }.getOrNull()
        availableTags = opts?.availableTags?.sortedBy { it.title } ?: emptyList()
        availableRatings = opts?.availableContentRating?.toList().orEmpty()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.filter), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { include = emptySet(); exclude = emptySet(); rating = emptySet() }) {
                    Text(stringResource(Res.string.reset_filter))
                }
            }

            // Sort (Urutkan)
            if (sortOrders.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.sort_order))
                SortDropdown(current = sort, options = sortOrders, onSelect = { sort = it })
            }

            when (val tags = availableTags) {
                null -> CircularProgressIndicator()
                else -> if (tags.isNotEmpty()) {
                    // Genre (include)
                    SectionHeader(stringResource(Res.string.genres), expandable = tags.size > COLLAPSED_TAGS, expanded = genreExpanded, onToggle = { genreExpanded = !genreExpanded })
                    TagChips(
                        tags = if (genreExpanded) tags else tags.take(COLLAPSED_TAGS),
                        selected = include,
                        onToggle = { tag ->
                            if (tag in include) include = include - tag
                            else { include = include + tag; exclude = exclude - tag } // a tag can't be both
                        },
                    )
                    // Exclude genre (Kecualikan genre)
                    SectionHeader(stringResource(Res.string.genres_exclude), expandable = tags.size > COLLAPSED_TAGS, expanded = excludeExpanded, onToggle = { excludeExpanded = !excludeExpanded })
                    TagChips(
                        tags = if (excludeExpanded) tags else tags.take(COLLAPSED_TAGS),
                        selected = exclude,
                        onToggle = { exclude = if (it in exclude) exclude - it else exclude + it; include = include - it },
                    )
                }
            }

            // Content rating (Peringkat konten)
            if (availableRatings.isNotEmpty()) {
                SectionHeader(stringResource(Res.string.content_rating))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableRatings.forEach { cr ->
                        FilterChip(
                            selected = cr in rating,
                            onClick = { rating = if (cr in rating) rating - cr else rating + cr },
                            label = { Text(contentRatingTitle(cr)) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    holder.tags.value = include
                    holder.tagsExclude.value = exclude
                    holder.contentRating.value = rating
                    settings.localListOrder = sort
                    holder.notifyApplied()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.done)) }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (expandable) {
            TextButton(onClick = onToggle) { Text(stringResource(Res.string.more)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<MangaTag>, selected: Set<MangaTag>, onToggle: (MangaTag) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(tag.title) },
            )
        }
    }
}

@Composable
private fun SortDropdown(current: SortOrder, options: List<SortOrder>, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(sortOrderTitle(current), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { order ->
                DropdownMenuItem(
                    text = { Text(sortOrderTitle(order)) },
                    onClick = { expanded = false; onSelect(order) },
                )
            }
        }
    }
}
