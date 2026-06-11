package org.nekosukuriputo.nekuva.settings.ui.tracker

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.IndexListPref
import org.nekosukuriputo.nekuva.settings.ui.components.MultiPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSettingsScreen(
    onBackClick: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
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
            BoolPref(settings, AppSettings.KEY_TRACKER_ENABLED, stringResource(Res.string.check_new_chapters_title), null, true)
            BoolPref(settings, AppSettings.KEY_TRACKER_WIFI_ONLY, stringResource(Res.string.only_using_wifi), stringResource(Res.string.tracker_wifi_only_summary), false)
            IndexListPref(
                settings, AppSettings.KEY_TRACKER_FREQUENCY, stringResource(Res.string.frequency_of_check),
                listOf(stringResource(Res.string.manual), stringResource(Res.string.less_frequently), stringResource(Res.string.system_default), stringResource(Res.string.more_frequently)), 2,
            )
            MultiPref(
                settings, AppSettings.KEY_TRACK_SOURCES, stringResource(Res.string.track_sources),
                listOf(stringResource(Res.string.favourites) to "favourites", stringResource(Res.string.history) to "history"),
                setOf("favourites"),
            )
            SettingsItem(title = stringResource(Res.string.favourites_categories), summary = stringResource(Res.string.coming_soon), enabled = false)
            SettingsItem(title = stringResource(Res.string.notifications_settings), summary = stringResource(Res.string.coming_soon), enabled = false)
            BoolPref(settings, AppSettings.KEY_TRACKER_NO_NSFW, stringResource(Res.string.disable_nsfw_notifications), stringResource(Res.string.disable_nsfw_notifications_summary), false)
            IndexListPref(
                settings, AppSettings.KEY_TRACKER_DOWNLOAD, stringResource(Res.string.download_new_chapters),
                listOf(stringResource(Res.string.never), stringResource(Res.string.manga_with_downloaded_chapters)), 0,
            )
        }
    }
}
