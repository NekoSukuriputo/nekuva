package org.nekosukuriputo.nekuva.settings.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.dp
import org.nekosukuriputo.nekuva.core.os.rememberBatteryOptimizationRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import nekuva.composeapp.generated.resources.automatic
import nekuva.composeapp.generated.resources.downloads
import nekuva.composeapp.generated.resources.local_manga_directories
import nekuva.composeapp.generated.resources.multiple_cbz_files
import nekuva.composeapp.generated.resources.preferred_download_format
import nekuva.composeapp.generated.resources.remove
import nekuva.composeapp.generated.resources.single_cbz_file
import nekuva.composeapp.generated.resources.specify_directory
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.core.prefs.DownloadFormat
import org.nekosukuriputo.nekuva.core.prefs.TriStateOption
import org.nekosukuriputo.nekuva.download.ui.dialog.pickMangaDirectory
import org.nekosukuriputo.nekuva.download.ui.dialog.supportsDirectoryPicker
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.IndexListPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSettingsScreen(
    viewModel: DownloadsSettingsViewModel = koinViewModel(),
    onBackClick: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val directories by viewModel.directories.collectAsState()
    val format by viewModel.format.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsCategoryHeader(stringResource(Res.string.local_manga_directories))
            for (dir in directories) {
                SettingsItem(
                    title = dir.name,
                    onClick = { viewModel.setDefault(dir.path) },
                    trailing = {
                        if (dir.isCustom) {
                            IconButton(onClick = { viewModel.removeDirectory(dir.path) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove))
                            }
                        }
                        RadioButton(selected = dir.isDefault, onClick = { viewModel.setDefault(dir.path) })
                    },
                )
            }
            if (supportsDirectoryPicker) {
                SettingsItem(
                    title = stringResource(Res.string.specify_directory),
                    icon = Icons.Default.Add,
                    onClick = {
                        scope.launch {
                            val path = pickMangaDirectory()
                            if (path != null) viewModel.addDirectory(path)
                        }
                    },
                )
            }

            SettingsSingleChoice(
                title = stringResource(Res.string.preferred_download_format),
                options = listOf(
                    stringResource(Res.string.automatic) to DownloadFormat.AUTOMATIC,
                    stringResource(Res.string.single_cbz_file) to DownloadFormat.SINGLE_CBZ,
                    stringResource(Res.string.multiple_cbz_files) to DownloadFormat.MULTIPLE_CBZ,
                ),
                selected = format,
                onSelect = { viewModel.setFormat(it) },
            )
            // Metered-network policy (Doki downloads_metered_network) — stored as TriStateOption name so the
            // download engine's gate (allowDownloadOnMeteredNetwork) reads it correctly.
            run {
                var metered by remember { mutableStateOf(settings.allowDownloadOnMeteredNetwork) }
                SettingsSingleChoice(
                    title = stringResource(Res.string.download_over_cellular),
                    options = listOf(
                        stringResource(Res.string.allow_always) to TriStateOption.ENABLED,
                        stringResource(Res.string.ask_every_time) to TriStateOption.ASK,
                        stringResource(Res.string.dont_allow) to TriStateOption.DISABLED,
                    ),
                    selected = metered,
                    onSelect = { settings.allowDownloadOnMeteredNetwork = it; metered = it },
                )
            }
            // Info hint (Doki downloads_settings_info): downloads run while the app is open.
            Text(
                text = stringResource(Res.string.downloads_settings_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Android battery-optimization exemption (Doki ignore_dose) — hidden on Desktop (N/A).
            val batteryRequest = rememberBatteryOptimizationRequest()
            if (batteryRequest != null) {
                SettingsItem(
                    title = stringResource(Res.string.disable_battery_optimization),
                    summary = stringResource(Res.string.disable_battery_optimization_summary_downloads),
                    onClick = batteryRequest,
                )
            }

            SettingsCategoryHeader(stringResource(Res.string.pages_saving))
            // Default "save page" directory (Doki pages_dir). Empty = platform default (Pictures/Nekuva).
            val pageSaveDir by viewModel.pageSaveDir.collectAsState()
            if (supportsDirectoryPicker) {
                SettingsItem(
                    title = stringResource(Res.string.default_page_save_dir),
                    summary = pageSaveDir ?: stringResource(Res.string.not_set),
                    onClick = {
                        scope.launch {
                            val path = pickMangaDirectory()
                            if (path != null) viewModel.setPageSaveDir(path)
                        }
                    },
                )
            } else {
                SettingsItem(
                    title = stringResource(Res.string.default_page_save_dir),
                    summary = pageSaveDir ?: stringResource(Res.string.not_set),
                )
            }
            BoolPref(settings, AppSettings.KEY_PAGES_SAVE_ASK, stringResource(Res.string.ask_for_dest_dir_every_time), null, true)
        }
    }
}
