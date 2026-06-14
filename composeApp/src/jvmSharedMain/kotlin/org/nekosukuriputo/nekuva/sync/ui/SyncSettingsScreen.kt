package org.nekosukuriputo.nekuva.sync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.email
import nekuva.composeapp.generated.resources.favourites
import nekuva.composeapp.generated.resources.history
import nekuva.composeapp.generated.resources.logout
import nekuva.composeapp.generated.resources.never
import nekuva.composeapp.generated.resources.password
import nekuva.composeapp.generated.resources.server_address
import nekuva.composeapp.generated.resources.sync
import nekuva.composeapp.generated.resources.sync_auth
import nekuva.composeapp.generated.resources.sync_auth_hint
import nekuva.composeapp.generated.resources.sync_host_description
import nekuva.composeapp.generated.resources.sync_now
import nekuva.composeapp.generated.resources.sync_settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.sync.domain.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
	onBackClick: () -> Unit,
	viewModel: SyncViewModel = koinViewModel(),
) {
	val state by viewModel.uiState.collectAsState()
	val syncState by viewModel.syncState.collectAsState()
	val isLoading by viewModel.isLoading.collectAsState()
	val snackbarHost = remember { SnackbarHostState() }

	androidx.compose.runtime.LaunchedEffect(Unit) {
		viewModel.onError.collect { event ->
			event?.consume { throwable ->
				snackbarHost.showSnackbar(throwable.message ?: throwable.toString())
			}
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(Res.string.sync)) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
					}
				},
			)
		},
		snackbarHost = { SnackbarHost(snackbarHost) },
	) { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
		) {
			if (state.isLoggedIn) {
				LoggedInContent(state, syncState, isLoading, viewModel)
			} else {
				LoginContent(state, isLoading, viewModel)
			}
		}
	}
}

@Composable
private fun LoginContent(
	state: SyncViewModel.UiState,
	isLoading: Boolean,
	viewModel: SyncViewModel,
) {
	var host by remember { mutableStateOf(state.host) }
	var email by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }

	Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Text(stringResource(Res.string.sync_auth_hint), style = MaterialTheme.typography.bodyMedium)
			OutlinedTextField(
				value = host,
				onValueChange = { host = it },
				label = { Text(stringResource(Res.string.server_address)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			OutlinedTextField(
				value = email,
				onValueChange = { email = it },
				label = { Text(stringResource(Res.string.email)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			OutlinedTextField(
				value = password,
				onValueChange = { password = it },
				label = { Text(stringResource(Res.string.password)) },
				singleLine = true,
				visualTransformation = PasswordVisualTransformation(),
				modifier = Modifier.fillMaxWidth(),
			)
			Button(
				onClick = { viewModel.login(host, email, password) },
				enabled = !isLoading && email.isNotBlank() && password.isNotBlank() && host.isNotBlank(),
				modifier = Modifier.align(Alignment.End),
			) {
				if (isLoading) {
					CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
				} else {
					Text(stringResource(Res.string.sync_auth))
				}
			}
		}
	}
	Text(
		text = stringResource(Res.string.sync_host_description),
		style = MaterialTheme.typography.bodySmall,
		modifier = Modifier.padding(horizontal = 16.dp),
	)
}

@Composable
private fun LoggedInContent(
	state: SyncViewModel.UiState,
	syncState: SyncState,
	isLoading: Boolean,
	viewModel: SyncViewModel,
) {
	val never = stringResource(Res.string.never)
	SettingsItem(
		title = state.email.orEmpty(),
		summary = state.host,
	)
	SettingsItem(
		title = stringResource(Res.string.logout),
		onClick = { viewModel.logout() },
	)

	SettingsCategoryHeader(stringResource(Res.string.sync_settings))
	Text(
		text = stringResource(Res.string.sync_host_description),
		style = MaterialTheme.typography.bodySmall,
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
	)
	SettingsItem(
		title = stringResource(Res.string.favourites),
		summary = formatLastSync(state.lastSyncFavourites, never),
		trailing = {
			Switch(checked = state.favouritesEnabled, onCheckedChange = { viewModel.setFavouritesEnabled(it) })
		},
	)
	SettingsItem(
		title = stringResource(Res.string.history),
		summary = formatLastSync(state.lastSyncHistory, never),
		trailing = {
			Switch(checked = state.historyEnabled, onCheckedChange = { viewModel.setHistoryEnabled(it) })
		},
	)

	val isSyncing = isLoading || syncState is SyncState.Running
	Button(
		onClick = { viewModel.syncNow() },
		enabled = !isSyncing,
		modifier = Modifier.fillMaxWidth().padding(16.dp),
	) {
		if (isSyncing) {
			CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
		} else {
			Text(stringResource(Res.string.sync_now))
		}
	}
	(syncState as? SyncState.Error)?.let {
		Text(
			text = it.message,
			color = MaterialTheme.colorScheme.error,
			style = MaterialTheme.typography.bodySmall,
			modifier = Modifier.padding(horizontal = 16.dp),
		)
	}
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun formatLastSync(ts: Long, never: String): String {
	if (ts <= 0L) return never
	// kotlin.time.Instant (stdlib) — kotlinx.datetime.Instant is gone in 0.7+ (NoClassDefFoundError on Desktop).
	val dt = Instant.fromEpochMilliseconds(ts).toLocalDateTime(TimeZone.currentSystemDefault())
	fun pad(v: Int) = v.toString().padStart(2, '0')
	return "${dt.date} ${pad(dt.hour)}:${pad(dt.minute)}"
}
