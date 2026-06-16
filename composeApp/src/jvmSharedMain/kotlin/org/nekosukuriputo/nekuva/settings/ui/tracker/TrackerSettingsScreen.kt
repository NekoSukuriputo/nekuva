package org.nekosukuriputo.nekuva.settings.ui.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.os.rememberBatteryOptimizationRequest
import org.nekosukuriputo.nekuva.core.os.rememberNotificationSettingsRequest
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.IndexListPref
import org.nekosukuriputo.nekuva.settings.ui.components.MultiPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSettingsScreen(
    onBackClick: () -> Unit,
    onTrackCategories: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    // tracker_enabled is the master switch; everything else depends on it (Doki dependency).
    var trackerEnabled by remember { mutableStateOf(settings.prefBoolean(AppSettings.KEY_TRACKER_ENABLED, true)) }
    // Reschedule the background tracker (Android WorkManager) when leaving, picking up enabled/freq/wifi changes.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runCatching { org.nekosukuriputo.nekuva.tracker.work.scheduleTracker() } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.check_for_new_chapters)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            BoolPref(
                settings, AppSettings.KEY_TRACKER_ENABLED, stringResource(Res.string.check_new_chapters_title),
                null, true, onChange = { trackerEnabled = it },
            )
            BoolPref(
                settings, AppSettings.KEY_TRACKER_WIFI_ONLY, stringResource(Res.string.only_using_wifi),
                stringResource(Res.string.tracker_wifi_only_summary), false, enabled = trackerEnabled,
            )
            IndexListPref(
                settings, AppSettings.KEY_TRACKER_FREQUENCY, stringResource(Res.string.frequency_of_check),
                listOf(stringResource(Res.string.manual), stringResource(Res.string.less_frequently), stringResource(Res.string.system_default), stringResource(Res.string.more_frequently)), 2,
                enabled = trackerEnabled,
            )
            MultiPref(
                settings, AppSettings.KEY_TRACK_SOURCES, stringResource(Res.string.track_sources),
                listOf(stringResource(Res.string.favourites) to "favourites", stringResource(Res.string.history) to "history"),
                setOf("favourites"),
            )
            // Which favourite categories are tracked (Doki track_categories) — opens the categories manager,
            // where each category has a "track" toggle that drives the tracked set (findIdsWithTrack).
            SettingsItem(
                title = stringResource(Res.string.favourites_categories),
                enabled = trackerEnabled,
                onClick = onTrackCategories,
            )
            // Android notification channels (sound/vibrate/light); hidden on Desktop (no notifications).
            val notificationsRequest = rememberNotificationSettingsRequest()
            if (notificationsRequest != null) {
                SettingsItem(
                    title = stringResource(Res.string.notifications_settings),
                    enabled = trackerEnabled,
                    onClick = notificationsRequest,
                )
            }
            BoolPref(
                settings, AppSettings.KEY_TRACKER_NO_NSFW, stringResource(Res.string.disable_nsfw_notifications),
                stringResource(Res.string.disable_nsfw_notifications_summary), false, enabled = trackerEnabled,
            )
            // Stored as TrackerDownloadStrategy NAME so trackerDownloadStrategy (getEnum) reads it correctly
            // (IndexListPref stored an index → selalu DISABLED). Drives auto-download in the Feed refresh.
            run {
                var strategy by remember { mutableStateOf(settings.trackerDownloadStrategy) }
                SettingsSingleChoice(
                    title = stringResource(Res.string.download_new_chapters),
                    options = listOf(
                        stringResource(Res.string.never) to org.nekosukuriputo.nekuva.core.prefs.TrackerDownloadStrategy.DISABLED,
                        stringResource(Res.string.manga_with_downloaded_chapters) to org.nekosukuriputo.nekuva.core.prefs.TrackerDownloadStrategy.DOWNLOADED,
                    ),
                    selected = strategy,
                    enabled = trackerEnabled,
                    onSelect = { settings.setPref(AppSettings.KEY_TRACKER_DOWNLOAD, it.name); strategy = it },
                )
            }

            HorizontalDivider()
            SettingsCategoryHeader(stringResource(Res.string.debug))
            // Battery-optimization exemption (Doki ignore_dose) — Android only; hidden on Desktop.
            val batteryRequest = rememberBatteryOptimizationRequest()
            if (batteryRequest != null) {
                SettingsItem(
                    title = stringResource(Res.string.disable_battery_optimization),
                    enabled = trackerEnabled,
                    onClick = batteryRequest,
                )
            }
            // Tracker warning (Doki track_warning) — info hint.
            Text(
                text = stringResource(Res.string.tracker_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
