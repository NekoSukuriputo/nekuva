package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice

/** Per-source settings (Doki SourceSettingsFragment): domain/mirror, sign-in, clear cookies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    onBackClick: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    viewModel: SourceSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (state.domains.size > 1) {
                SettingsSingleChoice(
                    title = stringResource(Res.string.domain),
                    options = state.domains.map { it to it },
                    selected = state.currentDomain,
                    onSelect = { viewModel.setDomain(it) },
                )
            }
            if (state.authUrl != null) {
                SettingsItem(
                    title = stringResource(Res.string.sign_in),
                    summary = state.username,
                    onClick = { state.authUrl?.let(onOpenBrowser) },
                )
            }
            SettingsItem(
                title = stringResource(Res.string.clear_cookies),
                onClick = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.clear_cookies)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCookies { }
                    confirmClear = false
                }) { Text(stringResource(Res.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}
