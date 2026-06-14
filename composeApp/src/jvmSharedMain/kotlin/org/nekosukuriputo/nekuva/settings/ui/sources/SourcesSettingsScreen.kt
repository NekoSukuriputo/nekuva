package org.nekosukuriputo.nekuva.settings.ui.sources

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.explore.data.SourcesSortOrder
import org.nekosukuriputo.nekuva.core.prefs.TriStateOption
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSwitch

/** Remote sources settings (Doki pref_sources). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSettingsScreen(
    onBackClick: () -> Unit,
    onManageSources: () -> Unit,
    onCatalog: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.remote_sources)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            var sortOrder by remember { mutableStateOf(settings.sourcesSortOrder) }
            SettingsSingleChoice(
                title = stringResource(Res.string.sort_order),
                options = SourcesSortOrder.entries.map { prettify(it.name) to it },
                selected = sortOrder,
                onSelect = { settings.sourcesSortOrder = it; sortOrder = it },
            )
            var grid by remember { mutableStateOf(settings.isSourcesGridMode) }
            SettingsSwitch(
                title = stringResource(Res.string.show_in_grid_view),
                checked = grid,
                onCheckedChange = { settings.isSourcesGridMode = it; grid = it },
            )
            SettingsItem(title = stringResource(Res.string.manage_sources), onClick = onManageSources)
            var allEnabled by remember { mutableStateOf(settings.isAllSourcesEnabled) }
            SettingsSwitch(
                title = stringResource(Res.string.enable_all_sources),
                summary = stringResource(Res.string.enable_all_sources_summary),
                checked = allEnabled,
                onCheckedChange = { settings.isAllSourcesEnabled = it; allEnabled = it },
            )
            SettingsItem(title = stringResource(Res.string.sources_catalog), onClick = onCatalog)

            var noNsfw by remember { mutableStateOf(settings.isNsfwContentDisabled) }
            SettingsSwitch(
                title = stringResource(Res.string.disable_nsfw),
                summary = stringResource(Res.string.disable_nsfw_summary),
                checked = noNsfw,
                onCheckedChange = { settings.isNsfwContentDisabled = it; noNsfw = it },
            )
            var incognitoNsfw by remember { mutableStateOf(settings.incognitoModeForNsfw) }
            SettingsSingleChoice(
                title = stringResource(Res.string.incognito_for_nsfw),
                options = listOf(
                    stringResource(Res.string.enabled) to TriStateOption.ENABLED,
                    stringResource(Res.string.ask) to TriStateOption.ASK,
                    stringResource(Res.string.disabled) to TriStateOption.DISABLED,
                ),
                selected = incognitoNsfw,
                onSelect = { settings.incognitoModeForNsfw = it; incognitoNsfw = it },
            )
            run {
                var v by remember { mutableStateOf(settings.prefBoolean(AppSettings.KEY_TAGS_WARNINGS, true)) }
                SettingsSwitch(
                    title = stringResource(Res.string.tags_warnings),
                    summary = stringResource(Res.string.tags_warnings_summary),
                    checked = v,
                    onCheckedChange = { settings.setPref(AppSettings.KEY_TAGS_WARNINGS, it); v = it },
                )
            }
            run {
                var v by remember { mutableStateOf(settings.prefBoolean(AppSettings.KEY_MIRROR_SWITCHING, false)) }
                SettingsSwitch(
                    title = stringResource(Res.string.mirror_switching),
                    summary = stringResource(Res.string.mirror_switching_summary),
                    checked = v,
                    onCheckedChange = { settings.setPref(AppSettings.KEY_MIRROR_SWITCHING, it); v = it },
                )
            }
            run {
                var v by remember { mutableStateOf(settings.prefBoolean(AppSettings.KEY_HANDLE_LINKS, false)) }
                SettingsSwitch(
                    title = stringResource(Res.string.handle_links),
                    summary = stringResource(Res.string.handle_links_summary),
                    checked = v,
                    onCheckedChange = { settings.setPref(AppSettings.KEY_HANDLE_LINKS, it); v = it },
                )
            }
        }
    }
}

private fun prettify(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
