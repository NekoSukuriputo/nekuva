package org.nekosukuriputo.nekuva.settings.ui.services

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

// Discord Rich Presence is intentionally hidden (personal app — no community/RPC). The implementation
// (DiscordRpcManager / KizzyRPC gateway / login webview) is kept intact; flip this to true to re-enable
// the Settings → Services menu for it.
private const val SHOW_DISCORD_RPC_MENU = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesSettingsScreen(
    onBackClick: () -> Unit,
    onScrobblerLogin: (serviceId: Int) -> Unit = {},
    onDiscordLogin: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onSuggestionsClick: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    val soon = stringResource(Res.string.coming_soon)
    val scrobblerVm = koinInject<ScrobblerConfigViewModel>()
    val scrobblerItems by scrobblerVm.items.collectAsState()
    val signInLabel = stringResource(Res.string.sign_in)
    val logoutLabel = stringResource(Res.string.logout)
    // Kitsu uses OAuth2 password grant (no webview), so its sign-in opens a credentials dialog.
    var showKitsuLogin by remember { mutableStateOf(false) }
    // Reschedule the background suggestions worker (Android) when leaving, picking up enable/wifi changes.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runCatching { org.nekosukuriputo.nekuva.suggestions.work.scheduleSuggestions() } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.services)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsItem(title = stringResource(Res.string.sync), summary = stringResource(Res.string.sync_title), onClick = onSyncClick)
            // Suggestions (Doki): enable toggle + open the generated list. List access enabled when on.
            var suggestionsOn by remember { mutableStateOf(settings.isSuggestionsEnabled) }
            BoolPref(
                settings, AppSettings.KEY_SUGGESTIONS, stringResource(Res.string.suggestions),
                stringResource(Res.string.suggestions_summary), false, onChange = { suggestionsOn = it },
            )
            SettingsItem(
                title = stringResource(Res.string.suggestions),
                summary = stringResource(Res.string.show_all),
                enabled = suggestionsOn,
                onClick = onSuggestionsClick,
            )
            BoolPref(settings, AppSettings.KEY_RELATED_MANGA, stringResource(Res.string.related_manga), stringResource(Res.string.related_manga_summary), true)
            BoolPref(settings, AppSettings.KEY_STATS_ENABLED, stringResource(Res.string.reading_stats), null, false)
            BoolPref(settings, AppSettings.KEY_READING_TIME, stringResource(Res.string.reading_time_estimation), stringResource(Res.string.reading_time_estimation_summary), true)

            SettingsCategoryHeader(stringResource(Res.string.tracking))
            // Migrated scrobblers (Shikimori / AniList / MAL / Kitsu). Functional once a real client id is
            // set in ScrobblerConfig; until then shown disabled. Logged-in = tap to log out, else sign in.
            scrobblerItems.forEach { item ->
                val title = stringResource(
                    when (item.service) {
                        ScrobblerService.SHIKIMORI -> Res.string.shikimori
                        ScrobblerService.ANILIST -> Res.string.anilist
                        ScrobblerService.MAL -> Res.string.mal
                        ScrobblerService.KITSU -> Res.string.kitsu
                    },
                )
                when {
                    !item.isConfigured -> SettingsItem(title = title, summary = soon, enabled = false)
                    item.isEnabled -> SettingsItem(title = title, summary = logoutLabel, onClick = { scrobblerVm.logout(item.service) })
                    item.service == ScrobblerService.KITSU ->
                        SettingsItem(title = title, summary = signInLabel, onClick = { showKitsuLogin = true })
                    else -> SettingsItem(title = title, summary = signInLabel, onClick = { onScrobblerLogin(item.service.id) })
                }
            }
            // Discord Rich Presence (Doki DiscordRpc): Android gateway via KizzyRPC; Desktop no-op.
            // Enable toggle + token login (webview scrape) + skip-NSFW. Hidden by SHOW_DISCORD_RPC_MENU
            // (personal app) — the implementation stays compiled; only the menu is gated off.
            if (SHOW_DISCORD_RPC_MENU) {
                BoolPref(settings, AppSettings.KEY_DISCORD_RPC, stringResource(Res.string.discord_rpc), stringResource(Res.string.discord_rpc_summary), false)
                val discordEnabled by settings.observeBoolean(AppSettings.KEY_DISCORD_RPC, false)
                    .collectAsState(initial = settings.isDiscordRpcEnabled)
                if (discordEnabled) {
                    // Re-read the token whenever it changes (login/logout) so the row label stays live.
                    val tokenTick by settings.keyChangeFlow(AppSettings.KEY_DISCORD_TOKEN).collectAsState(initial = Unit)
                    val hasToken = remember(tokenTick) { settings.discordToken != null }
                    if (hasToken) {
                        SettingsItem(title = stringResource(Res.string.discord_token), summary = logoutLabel, onClick = { settings.discordToken = null })
                    } else {
                        SettingsItem(title = stringResource(Res.string.discord_token), summary = signInLabel, onClick = onDiscordLogin)
                    }
                    BoolPref(settings, AppSettings.KEY_DISCORD_RPC_SKIP_NSFW, stringResource(Res.string.discord_rpc_skip_nsfw), null, false)
                }
            }
        }
    }

    if (showKitsuLogin) {
        KitsuLoginDialog(
            onConfirm = { username, password ->
                showKitsuLogin = false
                scrobblerVm.completeAuth(ScrobblerService.KITSU, "$username;$password")
            },
            onDismiss = { showKitsuLogin = false },
        )
    }
}

/** Username/password prompt for Kitsu's OAuth2 password grant (Doki's KitsuAuthActivity equivalent). */
@Composable
private fun KitsuLoginDialog(
    onConfirm: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.kitsu)) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank(),
            ) { Text(stringResource(Res.string.sign_in)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
