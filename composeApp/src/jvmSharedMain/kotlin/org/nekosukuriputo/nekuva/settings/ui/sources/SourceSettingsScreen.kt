package org.nekosukuriputo.nekuva.settings.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSwitch

/**
 * Per-source settings (Doki SourceSettingsFragment + pref_source/pref_source_parser): enable/disable,
 * domain/mirror, user agent, optional parser toggles, sign-in, clear cookies, captcha + slowdown toggles,
 * and open-in-browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    onBackClick: () -> Unit,
    onOpenBrowser: (url: String) -> Unit,
    viewModel: SourceSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var editDomain by remember { mutableStateOf(false) }
    var editUserAgent by remember { mutableStateOf(false) }

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
            if (!state.isValidSource) {
                SettingsItem(title = stringResource(Res.string.unsupported_source))
                return@Column
            }

            // Enable source — prominent header toggle (Doki preference_toggle_header).
            if (state.isEnableVisible) {
                EnableHeader(
                    enabled = state.isEnabled,
                    onToggle = { viewModel.setEnabled(it) },
                )
            }

            // Ranah web (domain / mirror).
            SettingsItem(
                title = stringResource(Res.string.domain),
                summary = state.domain.ifEmpty { state.domainDefault },
                onClick = { editDomain = true },
            )

            // Tajuk Agen Pengguna (user agent).
            if (state.userAgentSupported) {
                SettingsItem(
                    title = stringResource(Res.string.user_agent),
                    summary = state.userAgent.ifEmpty { state.userAgentDefault },
                    onClick = { editUserAgent = true },
                )
            }

            // Optional parser toggles.
            if (state.showSuspiciousSupported) {
                SettingsSwitch(
                    title = stringResource(Res.string.show_suspicious_content),
                    checked = state.showSuspicious,
                    onCheckedChange = { viewModel.setShowSuspicious(it) },
                )
            }
            if (state.splitByTranslationsSupported) {
                SettingsSwitch(
                    title = stringResource(Res.string.split_by_translations),
                    summary = stringResource(Res.string.split_by_translations_summary),
                    checked = state.splitByTranslations,
                    onCheckedChange = { viewModel.setSplitByTranslations(it) },
                )
            }
            if (state.imageServerOptions.isNotEmpty()) {
                val automatic = stringResource(Res.string.automatic)
                SettingsSingleChoice(
                    title = stringResource(Res.string.image_server),
                    options = state.imageServerOptions.map { (value, label) ->
                        label.ifEmpty { automatic } to value
                    },
                    selected = state.imageServer ?: "",
                    onSelect = { viewModel.setImageServer(it) },
                )
            }

            HorizontalDivider()

            // Sign in (auth) — only when the source supports it.
            if (state.authUrl != null) {
                SettingsItem(
                    title = stringResource(Res.string.sign_in),
                    summary = state.username,
                    onClick = { state.authUrl?.let(onOpenBrowser) },
                )
            }

            // Bersihkan kuki (clear cookies).
            SettingsItem(
                title = stringResource(Res.string.clear_cookies),
                summary = stringResource(Res.string.clear_source_cookies_summary),
                onClick = { confirmClear = true },
            )

            // Nonaktifkan notifikasi captcha.
            SettingsSwitch(
                title = stringResource(Res.string.disable_captcha_notifications),
                summary = stringResource(Res.string.disable_captcha_notifications_summary),
                checked = state.captchaDisabled,
                onCheckedChange = { viewModel.setCaptchaDisabled(it) },
            )

            // Perlambat unduhan.
            SettingsSwitch(
                title = stringResource(Res.string.download_slowdown),
                summary = stringResource(Res.string.download_slowdown_summary),
                checked = state.slowdownEnabled,
                onCheckedChange = { viewModel.setSlowdown(it) },
            )

            // Buka di peramban web.
            state.browserUrl?.let { url ->
                HorizontalDivider()
                SettingsItem(
                    title = stringResource(Res.string.open_in_browser),
                    summary = url,
                    icon = Icons.Default.Public,
                    onClick = { onOpenBrowser(url) },
                )
            }
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

    if (editDomain) {
        EditValueDialog(
            title = stringResource(Res.string.domain),
            initial = state.domain,
            placeholder = state.domainDefault,
            presets = state.domainPresets.takeIf { it.size > 1 }.orEmpty(),
            onReset = { viewModel.resetDomain(); editDomain = false },
            onSave = { viewModel.setDomain(it); editDomain = false },
            onDismiss = { editDomain = false },
        )
    }

    if (editUserAgent) {
        EditValueDialog(
            title = stringResource(Res.string.user_agent),
            initial = state.userAgent,
            placeholder = state.userAgentDefault,
            presets = state.userAgentPresets,
            onReset = { viewModel.setUserAgent(""); editUserAgent = false },
            onSave = { viewModel.setUserAgent(it); editUserAgent = false },
            onDismiss = { editUserAgent = false },
        )
    }
}

/** Prominent enable/disable header (Doki preference_toggle_header): a tonal card with a switch. */
@Composable
private fun EnableHeader(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.enable_source),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** Doki's EditTextPreference dialog: editable field + preset quick-fills + a neutral Reset button. */
@Composable
private fun EditValueDialog(
    title: String,
    initial: String,
    placeholder: String,
    presets: List<String>,
    onReset: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = placeholder.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (presets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        for (preset in presets) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { text = preset }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(stringResource(Res.string.reset)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}
