package org.nekosukuriputo.nekuva.settings.ui.services

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
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesSettingsScreen(
    onBackClick: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val soon = stringResource(Res.string.coming_soon)
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
            SettingsItem(title = stringResource(Res.string.sync), summary = soon, enabled = false)
            SettingsItem(title = stringResource(Res.string.suggestions), summary = soon, enabled = false)
            BoolPref(settings, AppSettings.KEY_RELATED_MANGA, stringResource(Res.string.related_manga), stringResource(Res.string.related_manga_summary), true)
            BoolPref(settings, AppSettings.KEY_STATS_ENABLED, stringResource(Res.string.reading_stats), null, false)
            BoolPref(settings, AppSettings.KEY_READING_TIME, stringResource(Res.string.reading_time_estimation), stringResource(Res.string.reading_time_estimation_summary), true)

            SettingsCategoryHeader(stringResource(Res.string.tracking))
            SettingsItem(title = stringResource(Res.string.anilist), summary = soon, enabled = false)
            SettingsItem(title = stringResource(Res.string.kitsu), summary = soon, enabled = false)
            SettingsItem(title = stringResource(Res.string.mal), summary = soon, enabled = false)
            SettingsItem(title = stringResource(Res.string.shikimori), summary = soon, enabled = false)
            SettingsItem(title = stringResource(Res.string.discord_rpc), summary = stringResource(Res.string.discord_rpc_summary), enabled = false)
        }
    }
}
