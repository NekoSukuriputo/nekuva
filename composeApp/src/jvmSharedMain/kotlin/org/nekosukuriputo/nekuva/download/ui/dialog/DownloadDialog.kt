package org.nekosukuriputo.nekuva.download.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.automatic
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.chapter_selection_hint
import nekuva.composeapp.generated.resources.chapters
import nekuva.composeapp.generated.resources.chapters_all
import nekuva.composeapp.generated.resources.destination_directory
import nekuva.composeapp.generated.resources.download_cellular_confirm
import nekuva.composeapp.generated.resources.download_over_cellular
import nekuva.composeapp.generated.resources.download_option_all_chapters
import nekuva.composeapp.generated.resources.download_option_all_unread
import nekuva.composeapp.generated.resources.download_option_first_n_chapters
import nekuva.composeapp.generated.resources.download_option_next_unread_n_chapters
import nekuva.composeapp.generated.resources.download_option_whole_manga
import nekuva.composeapp.generated.resources.more_options
import nekuva.composeapp.generated.resources.multiple_cbz_files
import nekuva.composeapp.generated.resources.preferred_download_format
import nekuva.composeapp.generated.resources.save
import nekuva.composeapp.generated.resources.save_manga
import nekuva.composeapp.generated.resources.single_cbz_file
import nekuva.composeapp.generated.resources.specify_directory
import nekuva.composeapp.generated.resources.start_download
import nekuva.composeapp.generated.resources.system_default
import nekuva.composeapp.generated.resources.unknown
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.download.domain.ChaptersSelectMacro

private enum class DownloadOption { WHOLE_MANGA, WHOLE_BRANCH, FIRST, UNREAD }

@Composable
fun DownloadDialog(
    viewModel: DownloadDialogViewModel,
    onDismiss: () -> Unit,
    onScheduled: (startedNow: Boolean) -> Unit,
) {
    val manga by viewModel.manga.collectAsState()
    val options by viewModel.chaptersSelectOptions.collectAsState()
    val destinations by viewModel.destinations.collectAsState()
    val isOptionsLoading by viewModel.isOptionsLoading.collectAsState()
    val defaultFormat by viewModel.defaultFormat.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedOption by remember { mutableStateOf(DownloadOption.WHOLE_MANGA) }
    var startNow by remember { mutableStateOf(true) }
    var showMore by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf<DownloadFormat?>(null) }
    var selectedDestIndex by remember { mutableStateOf(0) }
    // Metered-network prompt (Doki downloads_metered_network = ASK): confirm before downloading on cellular.
    val settings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
    val networkState = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.os.NetworkState>()
    var pendingMeteredConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(defaultFormat) { if (selectedFormat == null) selectedFormat = defaultFormat }
    LaunchedEffect(Unit) { viewModel.onScheduled.collect { onScheduled(it) } }
    LaunchedEffect(Unit) { viewModel.onError.collect { onDismiss() } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp).heightIn(max = 600.dp)) {
                Text(
                    text = stringResource(Res.string.save_manga),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                // Scrollable middle — flexes so Cancel/Save below stay pinned even when "More options" is open.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = manga?.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )

                    OptionRow(
                        selected = selectedOption == DownloadOption.WHOLE_MANGA,
                        title = stringResource(Res.string.download_option_whole_manga),
                        subtitle = options.wholeManga.chaptersCount
                            .takeIf { it > 0 }
                            ?.let { pluralStringResource(Res.plurals.chapters, it, it) },
                        onClick = { selectedOption = DownloadOption.WHOLE_MANGA },
                    )
                    options.wholeBranch?.let { branch ->
                        OptionRow(
                            selected = selectedOption == DownloadOption.WHOLE_BRANCH,
                            title = stringResource(
                                Res.string.download_option_all_chapters,
                                branch.selectedBranch ?: stringResource(Res.string.unknown),
                            ),
                            subtitle = branch.chaptersCount
                                .takeIf { it > 0 }
                                ?.let { pluralStringResource(Res.plurals.chapters, it, it) },
                            onClick = { selectedOption = DownloadOption.WHOLE_BRANCH },
                            trailing = {
                                val branches = branch.branches.keys.toList()
                                ChevronPicker(
                                    enabled = selectedOption == DownloadOption.WHOLE_BRANCH && branches.size > 1,
                                    options = branches.map { (it ?: "") to it },
                                    onPick = { viewModel.setSelectedBranch(it) },
                                )
                            },
                        )
                    }
                    options.firstChapters?.let { first ->
                        OptionRow(
                            selected = selectedOption == DownloadOption.FIRST,
                            title = stringResource(
                                Res.string.download_option_first_n_chapters,
                                pluralStringResource(Res.plurals.chapters, first.chaptersCount, first.chaptersCount),
                            ),
                            subtitle = first.branch,
                            onClick = { selectedOption = DownloadOption.FIRST },
                            trailing = {
                                ChevronPicker(
                                    enabled = selectedOption == DownloadOption.FIRST,
                                    options = chaptersCountSequence(first.maxAvailableCount)
                                        .map { it.toString() to it }.toList(),
                                    onPick = { viewModel.setFirstChaptersCount(it) },
                                )
                            },
                        )
                    }
                    options.unreadChapters?.let { unread ->
                        OptionRow(
                            selected = selectedOption == DownloadOption.UNREAD,
                            title = if (unread.chaptersCount == Int.MAX_VALUE) {
                                stringResource(Res.string.download_option_all_unread)
                            } else {
                                stringResource(
                                    Res.string.download_option_next_unread_n_chapters,
                                    pluralStringResource(Res.plurals.chapters, unread.chaptersCount, unread.chaptersCount),
                                )
                            },
                            subtitle = null,
                            onClick = { selectedOption = DownloadOption.UNREAD },
                            trailing = {
                                val allLabel = stringResource(Res.string.chapters_all)
                                ChevronPicker(
                                    enabled = selectedOption == DownloadOption.UNREAD,
                                    options = chaptersCountSequence(unread.maxAvailableCount)
                                        .map { it.toString() to it }.toList() + (allLabel to Int.MAX_VALUE),
                                    onPick = { viewModel.setUnreadChaptersCount(it) },
                                )
                            },
                        )
                    }

                    if (isOptionsLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp))
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.width(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.chapter_selection_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.start_download),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(checked = startNow, onCheckedChange = { startNow = it })
                    }

                    // "More options" — with a rotating chevron so it's clearly expandable.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMore = !showMore }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.more_options),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (showMore) 180f else 0f),
                        )
                    }
                    if (showMore) {
                        Text(
                            text = stringResource(Res.string.destination_directory),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        DestinationPicker(
                            destinations = destinations,
                            selectedIndex = selectedDestIndex,
                            onSelect = { selectedDestIndex = it },
                            onBrowse = {
                                scope.launch {
                                    val path = pickMangaDirectory()
                                    if (path != null) selectedDestIndex = viewModel.addDestination(path)
                                }
                            },
                        )
                        Text(
                            text = stringResource(Res.string.preferred_download_format),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        DropdownPicker(
                            label = formatLabel(selectedFormat ?: DownloadFormat.AUTOMATIC),
                            options = DownloadFormat.entries.map { formatLabel(it) to it },
                            onPick = { selectedFormat = it },
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    TextButton(
                        enabled = manga != null,
                        onClick = {
                            val macro: ChaptersSelectMacro? = when (selectedOption) {
                                DownloadOption.WHOLE_MANGA -> options.wholeManga
                                DownloadOption.WHOLE_BRANCH -> options.wholeBranch
                                DownloadOption.FIRST -> options.firstChapters
                                DownloadOption.UNREAD -> options.unreadChapters
                            }
                            if (macro != null) {
                                val doConfirm = {
                                    viewModel.confirm(
                                        startNow = startNow,
                                        macro = macro,
                                        format = selectedFormat,
                                        destination = destinations.getOrNull(selectedDestIndex),
                                    )
                                }
                                // ASK + on a metered network → confirm "download over cellular?" first.
                                if (startNow &&
                                    settings.allowDownloadOnMeteredNetwork == org.nekosukuriputo.nekuva.core.prefs.TriStateOption.ASK &&
                                    networkState.isMetered()
                                ) {
                                    pendingMeteredConfirm = doConfirm
                                } else {
                                    doConfirm()
                                }
                            }
                        },
                    ) { Text(stringResource(Res.string.save)) }
                }
            }
        }
    }

    // "Download over cellular?" confirmation (Doki metered ASK).
    pendingMeteredConfirm?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingMeteredConfirm = null },
            title = { Text(stringResource(Res.string.download_over_cellular)) },
            text = { Text(stringResource(Res.string.download_cellular_confirm)) },
            confirmButton = {
                TextButton(onClick = { pendingMeteredConfirm = null; action() }) {
                    Text(stringResource(Res.string.start_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMeteredConfirm = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun formatLabel(format: DownloadFormat): String = when (format) {
    DownloadFormat.AUTOMATIC -> stringResource(Res.string.automatic)
    DownloadFormat.SINGLE_CBZ -> stringResource(Res.string.single_cbz_file)
    DownloadFormat.MULTIPLE_CBZ -> stringResource(Res.string.multiple_cbz_files)
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** A plain expand chevron that opens a dropdown of choices (the chosen value is reflected in the row title). */
@Composable
private fun <T> ChevronPicker(
    enabled: Boolean,
    options: List<Pair<String, T>>,
    onPick: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((text, value) in options) {
                DropdownMenuItem(
                    text = { Text(text.ifEmpty { stringResource(Res.string.unknown) }) },
                    onClick = { expanded = false; onPick(value) },
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownPicker(
    label: String,
    options: List<Pair<String, T>>,
    onPick: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((text, value) in options) {
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { expanded = false; onPick(value) },
                )
            }
        }
    }
}

@Composable
private fun DestinationPicker(
    destinations: List<DownloadDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onBrowse: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val systemDefault = stringResource(Res.string.system_default)
    val current = destinations.getOrNull(selectedIndex)
    val currentLabel = current?.let { it.name ?: systemDefault } ?: systemDefault
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            destinations.forEachIndexed { index, dest ->
                DropdownMenuItem(
                    text = { Text(dest.name ?: systemDefault) },
                    onClick = { expanded = false; onSelect(index) },
                )
            }
            if (supportsDirectoryPicker) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.specify_directory)) },
                    onClick = { expanded = false; onBrowse() },
                )
            }
        }
    }
}

private fun chaptersCountSequence(max: Int): Sequence<Int> = sequence {
    if (max <= 0) return@sequence
    yield(1)
    var seed = 5
    while (seed < max) {
        yield(seed)
        seed += when {
            seed < 20 -> 5
            seed < 60 -> 10
            else -> 20
        }
    }
    yield(max)
}
