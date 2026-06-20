package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.local.data.LocalMangaRepository
import org.nekosukuriputo.nekuva.local.domain.LocalFilterHolder
import org.nekosukuriputo.nekuva.parsers.model.MangaTag
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.done
import nekuva.composeapp.generated.resources.filter
import nekuva.composeapp.generated.resources.nothing_found
import nekuva.composeapp.generated.resources.reset_filter

/**
 * Local-library filter sheet (Doki local filter): pick tags present in the local library; applying writes
 * them to [LocalFilterHolder] which the local list observes and re-queries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocalFilterSheet(onDismiss: () -> Unit) {
    val repo = koinInject<LocalMangaRepository>()
    val holder = koinInject<LocalFilterHolder>()
    var available by remember { mutableStateOf<List<MangaTag>?>(null) }
    var selected by remember { mutableStateOf(holder.tags.value) }
    LaunchedEffect(Unit) {
        available = runCatching { repo.getFilterOptions().availableTags.sortedBy { it.title } }.getOrDefault(emptyList())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.filter), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { selected = emptySet() }) { Text(stringResource(Res.string.reset_filter)) }
            }
            when (val tags = available) {
                null -> CircularProgressIndicator()
                else -> if (tags.isEmpty()) {
                    Text(stringResource(Res.string.nothing_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag in selected,
                                onClick = { selected = if (tag in selected) selected - tag else selected + tag },
                                label = { Text(tag.title) },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { holder.tags.value = selected; onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.done)) }
        }
    }
}
