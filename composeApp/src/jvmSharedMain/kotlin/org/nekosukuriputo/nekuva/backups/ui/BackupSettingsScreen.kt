package org.nekosukuriputo.nekuva.backups.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import nekuva.composeapp.generated.resources.backup_done
import nekuva.composeapp.generated.resources.backup_information
import nekuva.composeapp.generated.resources.backup_restore
import nekuva.composeapp.generated.resources.create_backup
import nekuva.composeapp.generated.resources.error
import nekuva.composeapp.generated.resources.periodic_backups
import nekuva.composeapp.generated.resources.restore_backup
import nekuva.composeapp.generated.resources.restore_done
import nekuva.composeapp.generated.resources.restore_summary
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
    val isBusy by viewModel.isBusy.collectAsState()
    val backupIo = rememberBackupIo()
    val snackbarHostState = remember { SnackbarHostState() }

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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column {
                SettingsItem(
                    title = stringResource(Res.string.create_backup),
                    summary = stringResource(Res.string.backup_information),
                    icon = Icons.Outlined.Backup,
                    enabled = !isBusy,
                    onClick = { viewModel.createBackup(backupIo, defaultBackupName()) },
                )
                SettingsItem(
                    title = stringResource(Res.string.restore_backup),
                    summary = stringResource(Res.string.restore_summary),
                    icon = Icons.Outlined.Restore,
                    enabled = !isBusy,
                    onClick = { viewModel.restoreBackup(backupIo) },
                )
                SettingsItem(
                    title = stringResource(Res.string.periodic_backups),
                    icon = Icons.Outlined.Schedule,
                    enabled = !isBusy,
                    onClick = onPeriodicClick,
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun defaultBackupName(): String = "nekuva_backup_${System.currentTimeMillis()}.zip"
