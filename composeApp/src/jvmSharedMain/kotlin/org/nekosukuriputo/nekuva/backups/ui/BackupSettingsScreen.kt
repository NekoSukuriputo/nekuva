package org.nekosukuriputo.nekuva.backups.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    viewModel: BackupViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onPeriodicClick: () -> Unit = {},
) {
    val restorePrompt by viewModel.restorePrompt.collectAsState()
    val backupIo = rememberBackupIo()
    val snackbarHostState = remember { SnackbarHostState() }

    restorePrompt?.let { prompt ->
        RestoreSectionsDialog(
            sections = prompt.sections,
            onConfirm = { viewModel.confirmRestore(it) },
            onDismiss = { viewModel.cancelRestore() },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BackupEvent.Created -> getString(Res.string.backup_done)
                is BackupEvent.Restored -> getString(Res.string.restore_done, event.result.restored)
                is BackupEvent.Error -> getString(Res.string.error) + ": " + (event.message ?: "")
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // No blocking spinner: backup/restore runs in the background (BackupRestoreManager) with a
        // notification + snackbar, so the screen stays usable and the user can navigate away (Doki parity).
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column {
                SettingsItem(
                    title = stringResource(Res.string.create_backup),
                    summary = stringResource(Res.string.backup_information),
                    icon = Icons.Outlined.Backup,
                    onClick = { viewModel.createBackup(backupIo, defaultBackupName()) },
                )
                SettingsItem(
                    title = stringResource(Res.string.restore_backup),
                    summary = stringResource(Res.string.restore_summary),
                    icon = Icons.Outlined.Restore,
                    onClick = { viewModel.restoreBackup(backupIo) },
                )
                SettingsItem(
                    title = stringResource(Res.string.periodic_backups),
                    icon = Icons.Outlined.Schedule,
                    onClick = onPeriodicClick,
                )
            }
        }
    }
}

private fun defaultBackupName(): String = "nekuva_backup_${System.currentTimeMillis()}.zip"

/** Lets the user choose which sections to restore from the picked backup (Doki restore section picker). */
@Composable
private fun RestoreSectionsDialog(
    sections: Set<org.nekosukuriputo.nekuva.backups.domain.BackupSection>,
    onConfirm: (Set<org.nekosukuriputo.nekuva.backups.domain.BackupSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { androidx.compose.runtime.mutableStateListOf(*sections.toTypedArray()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.restore_backup)) },
        text = {
            // Scrollable: with all 10 sections the list overflows the dialog and the bottom rows were
            // clipped (looked like a label-less checkbox).
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                for (section in sections) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (section in selected) selected.remove(section) else selected.add(section)
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = section in selected,
                            onCheckedChange = { if (it) selected.add(section) else selected.remove(section) },
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        Text(sectionLabel(section))
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(selected.toSet()) },
                enabled = selected.isNotEmpty(),
            ) { Text(stringResource(Res.string.restore_backup)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun sectionLabel(section: org.nekosukuriputo.nekuva.backups.domain.BackupSection): String = stringResource(
    when (section) {
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.HISTORY -> Res.string.history
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.CATEGORIES -> Res.string.favourites_categories
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.FAVOURITES -> Res.string.favourites
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.SETTINGS -> Res.string.settings
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.SETTINGS_READER_GRID -> Res.string.reader_actions
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.BOOKMARKS -> Res.string.bookmarks
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.SOURCES -> Res.string.remote_sources
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.SCROBBLING -> Res.string.tracking
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.STATS -> Res.string.statistics
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.SAVED_FILTERS -> Res.string.saved_filters
        org.nekosukuriputo.nekuva.backups.domain.BackupSection.INDEX -> Res.string.backup_restore
    },
)
