package org.nekosukuriputo.nekuva.download.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.cancel_all
import nekuva.composeapp.generated.resources.cancel_all_downloads_confirm
import nekuva.composeapp.generated.resources.canceled
import nekuva.composeapp.generated.resources.chapters
import nekuva.composeapp.generated.resources.confirm
import nekuva.composeapp.generated.resources.download_complete
import nekuva.composeapp.generated.resources.downloads
import nekuva.composeapp.generated.resources.error_occurred
import nekuva.composeapp.generated.resources.hours
import nekuva.composeapp.generated.resources.in_progress
import nekuva.composeapp.generated.resources.manga_downloading_
import nekuva.composeapp.generated.resources.minutes
import nekuva.composeapp.generated.resources.pause
import nekuva.composeapp.generated.resources.paused
import nekuva.composeapp.generated.resources.queued
import nekuva.composeapp.generated.resources.remove
import nekuva.composeapp.generated.resources.remove_completed
import nekuva.composeapp.generated.resources.remove_completed_downloads_confirm
import nekuva.composeapp.generated.resources.resume
import nekuva.composeapp.generated.resources.retry
import nekuva.composeapp.generated.resources.text_downloads_list_holder
import nekuva.composeapp.generated.resources.unknown
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.ui.components.EmptyState
import org.nekosukuriputo.nekuva.core.util.ext.calculateTimeAgo
import org.nekosukuriputo.nekuva.download.domain.ChapterDownloadStatus
import org.nekosukuriputo.nekuva.download.domain.DownloadChapterState
import org.nekosukuriputo.nekuva.download.domain.DownloadState
import org.nekosukuriputo.nekuva.download.domain.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = koinViewModel(),
    onBackClick: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val hasActive by viewModel.hasActiveWorks.collectAsState()
    val hasPaused by viewModel.hasPausedWorks.collectAsState()
    val hasCancellable by viewModel.hasCancellableWorks.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var confirmRemoveCompleted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (hasActive) {
                        IconButton(onClick = { viewModel.pauseAll() }) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(Res.string.pause))
                        }
                    }
                    if (hasPaused) {
                        IconButton(onClick = { viewModel.resumeAll() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(Res.string.resume))
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (hasCancellable) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.cancel_all)) },
                                onClick = { menuExpanded = false; confirmCancelAll = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.remove_completed)) },
                            onClick = { menuExpanded = false; confirmRemoveCompleted = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                message = stringResource(Res.string.text_downloads_list_holder),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = entries,
                    key = { entry ->
                        when (entry) {
                            is DownloadListEntry.QueuedHeader -> "h:queued"
                            is DownloadListEntry.InProgressHeader -> "h:active"
                            is DownloadListEntry.DateHeader -> "h:date:${entry.timestamp}"
                            is DownloadListEntry.DownloadRow -> entry.state.id
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is DownloadListEntry.QueuedHeader -> SectionHeader(stringResource(Res.string.queued))
                        is DownloadListEntry.InProgressHeader -> SectionHeader(stringResource(Res.string.in_progress))
                        is DownloadListEntry.DateHeader -> SectionHeader(calculateTimeAgo(entry.timestamp))
                        is DownloadListEntry.DownloadRow -> DownloadItem(
                            state = entry.state,
                            onPause = { viewModel.pause(entry.state.id) },
                            onResume = { viewModel.resume(entry.state.id) },
                            onCancel = { viewModel.cancel(entry.state.id) },
                            onRemove = { viewModel.remove(entry.state.id) },
                            onRetry = { viewModel.retry(entry.state.id) },
                            onRetryChapter = { chapterId -> viewModel.retryChapter(entry.state.id, chapterId) },
                        )
                    }
                }
            }
        }
    }

    if (confirmCancelAll) {
        AlertDialog(
            onDismissRequest = { confirmCancelAll = false },
            title = { Text(stringResource(Res.string.cancel_all)) },
            text = { Text(stringResource(Res.string.cancel_all_downloads_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmCancelAll = false; viewModel.cancelAll() }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelAll = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
    if (confirmRemoveCompleted) {
        AlertDialog(
            onDismissRequest = { confirmRemoveCompleted = false },
            title = { Text(stringResource(Res.string.remove_completed)) },
            text = { Text(stringResource(Res.string.remove_completed_downloads_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmRemoveCompleted = false; viewModel.removeCompleted() }) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveCompleted = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Doki-style per-manga download card: header + progress, expandable (capped, scrollable) chapter list, footer actions. */
@Composable
private fun DownloadItem(
    state: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onRetryChapter: (Long) -> Unit,
) {
    val isPaused = state.status == DownloadStatus.PAUSED
    // Active downloads start expanded (chapters visible); finished ones collapse like Doki.
    var expanded by remember(state.id) { mutableStateOf(!state.isFinished) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = state.manga.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(13f / 18f)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.manga.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    detailText(state)?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.status == DownloadStatus.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (state.chapters.isNotEmpty()) {
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                            )
                        }
                    }
                    if (state.status == DownloadStatus.RUNNING && !state.isIndeterminate) {
                        Text(
                            text = "${(state.percent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.status == DownloadStatus.RUNNING) {
                Spacer(Modifier.height(8.dp))
                if (state.isIndeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { state.percent }, modifier = Modifier.fillMaxWidth())
                }
            }

            if (expanded && state.chapters.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Cap the chapter list at ~7 rows; scroll within the card instead of stretching the screen.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 296.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.chapters.forEachIndexed { index, chapter ->
                        ChapterRow(
                            number = index + 1,
                            chapter = chapter,
                            paused = isPaused,
                            onRetry = { onRetryChapter(chapter.id) },
                        )
                    }
                }
            }

            val footerActions: @Composable (() -> Unit)? = when {
                state.canPause -> {
                    { CardActions(primaryLabel = stringResource(Res.string.pause), onPrimary = onPause, onCancel = onCancel) }
                }
                state.canResume -> {
                    { CardActions(primaryLabel = stringResource(Res.string.resume), onPrimary = onResume, onCancel = onCancel) }
                }
                state.status == DownloadStatus.QUEUED -> {
                    { CardActions(primaryLabel = null, onPrimary = null, onCancel = onCancel) }
                }
                // Finished with failed chapters → offer "Retry" (re-downloads only the failed ones), like Doki.
                state.canRetry -> {
                    { CardActions(primaryLabel = stringResource(Res.string.retry), onPrimary = onRetry, onCancel = onRemove, cancelLabel = stringResource(Res.string.remove)) }
                }
                else -> null
            }
            if (footerActions != null) {
                Spacer(Modifier.height(8.dp))
                footerActions()
            }
        }
    }
}

@Composable
private fun CardActions(
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    onCancel: () -> Unit,
    cancelLabel: String = stringResource(Res.string.cancel),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        if (primaryLabel != null && onPrimary != null) {
            OutlinedButton(onClick = onPrimary) { Text(primaryLabel) }
        }
        OutlinedButton(onClick = onCancel) { Text(cancelLabel) }
    }
}

@Composable
private fun ChapterRow(
    number: Int,
    chapter: DownloadChapterState,
    paused: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chapter.status == ChapterDownloadStatus.FAILED && !chapter.error.isNullOrEmpty()) {
                Text(
                    text = chapter.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (chapter.status) {
            ChapterDownloadStatus.DONE -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            // Failed → tappable refresh icon to retry just this chapter (Doki).
            ChapterDownloadStatus.FAILED -> IconButton(onClick = { onRetry?.invoke() }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(Res.string.retry),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // While paused, an in-flight chapter shows a pause icon instead of a live spinner.
            ChapterDownloadStatus.DOWNLOADING -> if (paused) {
                Icon(Icons.Default.Pause, contentDescription = stringResource(Res.string.paused))
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp).height(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            ChapterDownloadStatus.PENDING -> Unit
        }
    }
}

@Composable
private fun statusText(state: DownloadState): String = when (state.status) {
    DownloadStatus.QUEUED -> stringResource(Res.string.queued)
    DownloadStatus.RUNNING -> stringResource(Res.string.manga_downloading_)
    DownloadStatus.PAUSED -> stringResource(Res.string.paused)
    DownloadStatus.COMPLETED -> stringResource(Res.string.download_complete)
    DownloadStatus.FAILED -> stringResource(Res.string.error_occurred)
    DownloadStatus.CANCELLED -> stringResource(Res.string.canceled)
}

@Composable
private fun detailText(state: DownloadState): String? = when {
    state.status == DownloadStatus.FAILED -> state.errorMessage
    state.status == DownloadStatus.COMPLETED && state.downloadedChapters > 0 ->
        pluralStringResource(Res.plurals.chapters, state.downloadedChapters, state.downloadedChapters)
    state.status == DownloadStatus.RUNNING -> etaText(state)
    else -> null
}

/** Remaining-time line for an active download (coarse: hours or minutes; no seconds plural available). */
@Composable
private fun etaText(state: DownloadState): String? {
    if (state.eta <= 0L) return null
    val totalSeconds = state.eta / 1000
    return if (totalSeconds >= 3600) {
        val hours = (totalSeconds / 3600).toInt().coerceAtLeast(1)
        pluralStringResource(Res.plurals.hours, hours, hours)
    } else {
        val minutes = ((totalSeconds + 59) / 60).toInt().coerceAtLeast(1)
        pluralStringResource(Res.plurals.minutes, minutes, minutes)
    }
}
