package org.nekosukuriputo.nekuva.settings.ui.appearance

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.i18n.SUPPORTED_LANGUAGE_TAGS
import org.nekosukuriputo.nekuva.core.i18n.localeDisplayName
import org.nekosukuriputo.nekuva.core.i18n.recreateForLocale
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ColorScheme
import org.nekosukuriputo.nekuva.core.prefs.ListMode
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.IndexListPref
import org.nekosukuriputo.nekuva.settings.ui.components.MultiPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsCategoryHeader
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSlider
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBackClick: () -> Unit,
    onNavSections: () -> Unit = {},
    onProtectSetup: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // Color scheme (live — drives NekuvaTheme via the 9 static Doki palettes). Labels localized.
            var colorScheme by remember { mutableStateOf(settings.colorScheme) }
            val schemeLabels = mapOf(
                ColorScheme.DEFAULT to stringResource(Res.string.theme_name_totoro),
                ColorScheme.MIKU to stringResource(Res.string.theme_name_miku),
                ColorScheme.RENA to stringResource(Res.string.theme_name_asuka),
                ColorScheme.FROG to stringResource(Res.string.theme_name_mion),
                ColorScheme.BLUEBERRY to stringResource(Res.string.theme_name_rikka),
                ColorScheme.SAKURA to stringResource(Res.string.theme_name_sakura),
                ColorScheme.MAMIMI to stringResource(Res.string.theme_name_mamimi),
                ColorScheme.KANADE to stringResource(Res.string.theme_name_kanade),
                ColorScheme.ITSUKA to stringResource(Res.string.theme_name_itsuka),
            )
            SettingsSingleChoice(
                title = stringResource(Res.string.color_theme),
                options = ColorScheme.getAvailableList().map { (schemeLabels[it] ?: it.name) to it },
                selected = colorScheme,
                onSelect = { settings.colorScheme = it; colorScheme = it },
            )
            // Theme (live)
            var theme by remember { mutableStateOf(settings.theme) }
            SettingsSingleChoice(
                title = stringResource(Res.string.theme),
                options = listOf(
                    stringResource(Res.string.follow_system) to -1,
                    stringResource(Res.string.light) to 1,
                    stringResource(Res.string.dark) to 2,
                ),
                selected = theme,
                onSelect = { settings.theme = it; theme = it },
            )
            // AMOLED (live)
            var amoled by remember { mutableStateOf(settings.isAmoledTheme) }
            SettingsSwitch(
                title = stringResource(Res.string.black_dark_theme),
                summary = stringResource(Res.string.black_dark_theme_summary),
                checked = amoled,
                onCheckedChange = { settings.isAmoledTheme = it; amoled = it },
            )
            // Language (Doki app_locale): full translated-language catalog, applied at runtime.
            var locale by remember { mutableStateOf(settings.appLocales) }
            val followSystem = stringResource(Res.string.follow_system)
            val languageOptions = remember(followSystem) {
                listOf(followSystem to "") +
                    SUPPORTED_LANGUAGE_TAGS.map { localeDisplayName(it) to it }.sortedBy { it.first.lowercase() }
            }
            SettingsSingleChoice(
                title = stringResource(Res.string.language),
                options = languageOptions,
                selected = locale,
                onSelect = { settings.appLocales = it; locale = it; recreateForLocale() },
            )

            SettingsCategoryHeader(stringResource(Res.string.manga_list))
            var listMode by remember { mutableStateOf(settings.listMode) }
            SettingsSingleChoice(
                title = stringResource(Res.string.list_mode),
                options = listOf(
                    stringResource(Res.string.list) to ListMode.LIST,
                    stringResource(Res.string.detailed_list) to ListMode.DETAILED_LIST,
                    stringResource(Res.string.grid) to ListMode.GRID,
                ),
                selected = listMode,
                onSelect = { settings.listMode = it; listMode = it },
            )
            var gridSize by remember { mutableStateOf(settings.gridSize) }
            SettingsSlider(
                title = stringResource(Res.string.grid_size),
                value = gridSize,
                valueRange = 50..150,
                step = 5,
                valueLabel = "$gridSize%",
                onValueChange = { settings.gridSize = it; gridSize = it },
            )
            BoolPref(settings, AppSettings.KEY_QUICK_FILTER, stringResource(Res.string.show_quick_filters), stringResource(Res.string.show_quick_filters_summary), true)
            IndexListPref(
                settings, AppSettings.KEY_PROGRESS_INDICATORS, stringResource(Res.string.show_reading_indicators),
                listOf(stringResource(Res.string.disabled), stringResource(Res.string.percent_read), stringResource(Res.string.percent_left), stringResource(Res.string.chapters_read), stringResource(Res.string.chapters_left)),
                1,
            )
            MultiPref(
                settings, AppSettings.KEY_MANGA_LIST_BADGES, stringResource(Res.string.badges_in_lists),
                listOf(stringResource(Res.string.favourites) to "1", stringResource(Res.string.saved_manga) to "2"),
                setOf("1", "2"),
            )

            SettingsCategoryHeader(stringResource(Res.string.details))
            BoolPref(settings, AppSettings.KEY_COLLAPSE_DESCRIPTION, stringResource(Res.string.collapse_long_description), null, true)
            BoolPref(settings, AppSettings.KEY_PAGES_TAB, stringResource(Res.string.show_pages_thumbs), stringResource(Res.string.show_pages_thumbs_summary), true)
            IndexListPref(
                settings, AppSettings.KEY_DETAILS_TAB, stringResource(Res.string.default_tab),
                listOf(stringResource(Res.string.last_used), stringResource(Res.string.chapters), stringResource(Res.string.pages), stringResource(Res.string.bookmarks)),
                0,
            )

            SettingsCategoryHeader(stringResource(Res.string.main_screen))
            MultiPref(
                settings, AppSettings.KEY_SEARCH_SUGGESTION_TYPES, stringResource(Res.string.search_suggestions),
                emptyList(), emptySet(),
            )
            SettingsItem(title = stringResource(Res.string.main_screen_sections), onClick = onNavSections)
            BoolPref(settings, AppSettings.KEY_MAIN_FAB, stringResource(Res.string.main_screen_fab), stringResource(Res.string.main_screen_fab_summary), true)
            BoolPref(settings, AppSettings.KEY_NAV_LABELS, stringResource(Res.string.show_labels_in_navbar), null, true)
            BoolPref(settings, AppSettings.KEY_NAV_PINNED, stringResource(Res.string.pin_navigation_ui), null, false)
            BoolPref(settings, AppSettings.KEY_EXIT_CONFIRM, stringResource(Res.string.exit_confirmation), null, false)
            BoolPref(settings, AppSettings.KEY_SHORTCUTS, stringResource(Res.string.history_shortcuts), null, true)

            SettingsCategoryHeader(stringResource(Res.string.privacy))
            // App lock (Doki protect_app): ON → set up a password; OFF → clear it. Reflects live.
            val hasPassword by settings.observeAppPasswordSet().collectAsState(initial = settings.appPassword != null)
            SettingsSwitch(
                title = stringResource(Res.string.protect_application),
                summary = stringResource(Res.string.protect_application_summary),
                checked = hasPassword,
                onCheckedChange = { on -> if (on) onProtectSetup() else settings.appPassword = null },
            )
            IndexListPref(
                settings, AppSettings.KEY_SCREENSHOTS_POLICY, stringResource(Res.string.screenshots_policy),
                listOf(stringResource(Res.string.screenshots_allow), stringResource(Res.string.screenshots_block_nsfw), stringResource(Res.string.screenshots_block_incognito), stringResource(Res.string.screenshots_block_all)),
                0,
            )
        }
    }
}
