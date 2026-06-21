package org.nekosukuriputo.nekuva.details.ui.scrobbling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.scrobbling.common.domain.Scrobbler
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerManga
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingInfo
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblingStatus
import kotlin.math.roundToInt
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.close
import nekuva.composeapp.generated.resources.done
import nekuva.composeapp.generated.resources.error
import nekuva.composeapp.generated.resources.loading
import nekuva.composeapp.generated.resources.nothing_found
import nekuva.composeapp.generated.resources.rating
import nekuva.composeapp.generated.resources.remove
import nekuva.composeapp.generated.resources.search
import nekuva.composeapp.generated.resources.state
import nekuva.composeapp.generated.resources.status_completed
import nekuva.composeapp.generated.resources.status_dropped
import nekuva.composeapp.generated.resources.status_on_hold
import nekuva.composeapp.generated.resources.status_planned
import nekuva.composeapp.generated.resources.status_re_reading
import nekuva.composeapp.generated.resources.status_reading
import nekuva.composeapp.generated.resources.tracking

/** Brand names (not translatable) for the tracker services. */
fun scrobblerServiceTitle(service: ScrobblerService): String = when (service) {
    ScrobblerService.SHIKIMORI -> "Shikimori"
    ScrobblerService.ANILIST -> "AniList"
    ScrobblerService.MAL -> "MyAnimeList"
    ScrobblerService.KITSU -> "Kitsu"
}

@Composable
fun scrobblingStatusTitle(status: ScrobblingStatus): String = when (status) {
    ScrobblingStatus.PLANNED -> stringResource(Res.string.status_planned)
    ScrobblingStatus.READING -> stringResource(Res.string.status_reading)
    ScrobblingStatus.RE_READING -> stringResource(Res.string.status_re_reading)
    ScrobblingStatus.COMPLETED -> stringResource(Res.string.status_completed)
    ScrobblingStatus.ON_HOLD -> stringResource(Res.string.status_on_hold)
    ScrobblingStatus.DROPPED -> stringResource(Res.string.status_dropped)
}

/**
 * Tracking cards on the Details page (Doki recyclerViewScrobbling): one per linked tracker, showing the
 * matched title, status, chapter progress and rating. Tapping a card opens [ScrobblingInfoEditSheet].
 */
@Composable
fun ScrobblingInfoCards(
    infos: List<ScrobblingInfo>,
    onClick: (ScrobblingInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (infos.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        infos.forEach { info ->
            Card(
                onClick = { onClick(info) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        model = info.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(scrobblerServiceTitle(info.scrobbler), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(info.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val status = info.status?.let { scrobblingStatusTitle(it) }
                        val parts = buildList {
                            if (status != null) add(status)
                            if (info.chapter > 0) add("#${info.chapter}")
                            if (info.rating > 0f) add("${(info.rating * 10f).roundToInt()}/10")
                        }
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tracker selector (Doki ScrobblingSelectorSheet): pick an authorized service, search it (seeded with the
 * manga title), and tap a result to link. [onLink] performs the bind + initial status seeding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobblingSelectorSheet(
    scrobblers: List<Scrobbler>,
    mangaTitle: String,
    onLink: (Scrobbler, ScrobblerManga) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (scrobblers.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.nothing_found))
            }
            return@ModalBottomSheet
        }
        var serviceIndex by remember { mutableStateOf(0) }
        val scrobbler = scrobblers[serviceIndex.coerceIn(scrobblers.indices)]
        var query by remember { mutableStateOf(mangaTitle) }
        var results by remember { mutableStateOf<List<ScrobblerManga>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun doSearch() {
            scope.launch {
                loading = true; failed = false
                runCatching { scrobbler.findManga(query, 0) }
                    .onSuccess { results = it }
                    .onFailure { failed = true; results = emptyList() }
                loading = false
            }
        }
        LaunchedEffect(serviceIndex) { query = mangaTitle; doSearch() }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(Res.string.tracking), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            if (scrobblers.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scrobblers.forEachIndexed { i, s ->
                        FilterChip(
                            selected = i == serviceIndex,
                            onClick = { serviceIndex = i },
                            label = { Text(scrobblerServiceTitle(s.scrobblerService)) },
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { doSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
                    }
                },
            )
            Spacer(Modifier.size(8.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                failed -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.error), color = MaterialTheme.colorScheme.error) }
                results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.nothing_found)) }
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.id }) { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onLink(scrobbler, m); onDismiss() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = m.cover,
                                contentDescription = null,
                                modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                m.altName?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

/**
 * Edit a linked tracker entry (Doki ScrobblingInfoSheet): change status / rating, or unlink. Saving pushes
 * the change to the service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobblingInfoEditSheet(
    info: ScrobblingInfo,
    onUpdate: (rating: Float, status: ScrobblingStatus) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var status by remember { mutableStateOf(info.status ?: ScrobblingStatus.READING) }
        var rating by remember { mutableStateOf(info.rating) }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(scrobblerServiceTitle(info.scrobbler), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(12.dp))

            Text(stringResource(Res.string.state), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(4.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScrobblingStatus.entries.forEach { s ->
                    FilterChip(
                        selected = s == status,
                        onClick = { status = s },
                        label = { Text(scrobblingStatusTitle(s)) },
                    )
                }
            }
            Spacer(Modifier.size(12.dp))

            Text("${stringResource(Res.string.rating)}: ${(rating * 10f).roundToInt()}/10", style = MaterialTheme.typography.labelLarge)
            Slider(value = rating, onValueChange = { rating = it }, valueRange = 0f..1f)
            Spacer(Modifier.size(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onUnlink(); onDismiss() }) {
                    Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onUpdate(rating, status); onDismiss() }) {
                    Text(stringResource(Res.string.done))
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}
