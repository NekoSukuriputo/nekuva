package org.nekosukuriputo.nekuva.backups.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.backups.domain.TelegramBackupConfig
import org.nekosukuriputo.nekuva.backups.domain.TelegramBackupUploader
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.download.ui.dialog.pickMangaDirectory
import org.nekosukuriputo.nekuva.download.ui.dialog.supportsDirectoryPicker
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsEditText
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSlider

/** Periodic backup config (Doki PeriodicalBackupSettingsFragment): enable + dir + freq + trim/count + Telegram. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicBackupScreen(onBackClick: () -> Unit) {
    val settings = koinInject<AppSettings>()
    val uploader = koinInject<TelegramBackupUploader>()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    var enabled by remember { mutableStateOf(settings.isPeriodicalBackupEnabled) }
    var dir by remember { mutableStateOf(settings.periodicalBackupDirectory) }
    var freq by remember { mutableStateOf(settings.prefString(AppSettings.KEY_BACKUP_PERIODICAL_FREQUENCY, "7")) }
    var trim by remember { mutableStateOf(settings.prefBoolean(AppSettings.KEY_BACKUP_PERIODICAL_TRIM, true)) }
    var count by remember { mutableStateOf(settings.prefInt(AppSettings.KEY_BACKUP_PERIODICAL_COUNT, 10)) }
    var tgEnabled by remember { mutableStateOf(settings.isBackupTelegramUploadEnabled) }
    var chatId by remember { mutableStateOf(settings.backupTelegramChatId.orEmpty()) }

    // (Re)schedule the Android worker when leaving, picking up enable/frequency/Telegram changes.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runCatching { org.nekosukuriputo.nekuva.backups.work.scheduleBackup() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.periodic_backups)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            BoolPref(
                settings, AppSettings.KEY_BACKUP_PERIODICAL_ENABLED,
                stringResource(Res.string.periodic_backups_enable), null, false, onChange = { enabled = it },
            )
            if (supportsDirectoryPicker) {
                SettingsItem(
                    title = stringResource(Res.string.backups_output_directory),
                    summary = dir,
                    enabled = enabled,
                    onClick = {
                        scope.launch {
                            pickMangaDirectory()?.let { settings.periodicalBackupDirectory = it; dir = it }
                        }
                    },
                )
            }
            SettingsSingleChoice(
                title = stringResource(Res.string.backup_frequency),
                options = listOf(
                    stringResource(Res.string.frequency_every_6_hours) to "0.25",
                    stringResource(Res.string.frequency_every_day) to "1",
                    stringResource(Res.string.frequency_every_2_days) to "2",
                    stringResource(Res.string.frequency_once_per_week) to "7",
                    stringResource(Res.string.frequency_twice_per_month) to "15",
                    stringResource(Res.string.frequency_once_per_month) to "30",
                ),
                selected = freq,
                enabled = enabled,
                onSelect = { settings.setPref(AppSettings.KEY_BACKUP_PERIODICAL_FREQUENCY, it); freq = it },
            )
            BoolPref(
                settings, AppSettings.KEY_BACKUP_PERIODICAL_TRIM,
                stringResource(Res.string.delete_old_backups), stringResource(Res.string.delete_old_backups_summary),
                true, enabled = enabled, onChange = { trim = it },
            )
            if (trim) {
                SettingsSlider(
                    title = stringResource(Res.string.max_backups_count),
                    value = count,
                    valueRange = 1..32,
                    onValueChange = { settings.setPref(AppSettings.KEY_BACKUP_PERIODICAL_COUNT, it); count = it },
                    enabled = enabled,
                )
            }
            // Last backup time (Doki backup_periodic_last).
            val last = settings.prefString(AppSettings.KEY_BACKUP_PERIODICAL_LAST, "")
            val lastMs = last.toLongOrNull()
            if (lastMs != null) {
                SettingsItem(
                    title = stringResource(Res.string.last_backup),
                    summary = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastMs)),
                    enabled = false,
                )
            }

            // Telegram integration (Doki) — section ALWAYS shown for parity with Doki's periodic-backup screen
            // (uses the Kotatsu backup bot). It is fully functional only when the bot token is injected at build
            // (TelegramBackupConfig/TelegramSecrets via local.properties `tg_backup_bot_token`). Doki's open
            // source does NOT ship that token (it's a build secret), so until it's provided the controls are
            // shown disabled with a "coming soon" hint.
            // TODO(credentials): supply the Kotatsu backup bot token (local.properties `tg_backup_bot_token=...`)
            // to activate Telegram backup; without it this whole section stays "coming soon".
            val tgAvailable = TelegramBackupConfig.isAvailable
            val soon = stringResource(Res.string.coming_soon)
            SettingsCategoryHeader(stringResource(Res.string.telegram_integration))
            BoolPref(
                settings, AppSettings.KEY_BACKUP_TG_ENABLED,
                stringResource(Res.string.send_backups_telegram),
                if (tgAvailable) null else soon, false,
                enabled = enabled && tgAvailable, onChange = { tgEnabled = it },
            )
            SettingsEditText(
                title = stringResource(Res.string.telegram_chat_id),
                value = chatId,
                onValueChange = { settings.setPref(AppSettings.KEY_BACKUP_TG_CHAT, it); chatId = it },
                enabled = enabled && tgEnabled && tgAvailable,
            )
            SettingsItem(
                title = stringResource(Res.string.open_telegram_bot),
                summary = if (tgAvailable) stringResource(Res.string.open_telegram_bot_summary) else soon,
                enabled = enabled && tgEnabled && tgAvailable,
                onClick = {
                    // Doki openBotInApp: prefer the Telegram app (tg://), fall back to the web link.
                    val opened = runCatching {
                        uriHandler.openUri("tg://resolve?domain=${TelegramBackupConfig.BOT_NAME}")
                    }.isSuccess
                    if (!opened) runCatching { uriHandler.openUri(uploader.botUrl) }
                },
            )
            val testLabel = stringResource(Res.string.test_connection)
            val errorLabel = stringResource(Res.string.error)
            SettingsItem(
                title = testLabel,
                summary = if (tgAvailable) null else soon,
                enabled = enabled && tgEnabled && tgAvailable,
                onClick = {
                    scope.launch {
                        try {
                            uploader.sendTestMessage(getString(Res.string.backup_tg_echo))
                            snackbar.showSnackbar("$testLabel ✓")
                        } catch (e: Exception) {
                            snackbar.showSnackbar("$errorLabel: ${e.message ?: ""}")
                        }
                    }
                },
            )
        }
    }
}
