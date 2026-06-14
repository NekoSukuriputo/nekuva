package org.nekosukuriputo.nekuva.settings.ui.reader

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
import org.nekosukuriputo.nekuva.core.prefs.ReaderMode
import org.nekosukuriputo.nekuva.settings.ui.components.BoolPref
import org.nekosukuriputo.nekuva.settings.ui.components.IndexListPref
import org.nekosukuriputo.nekuva.settings.ui.components.MultiPref
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsItem
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(
    onBackClick: () -> Unit,
    onTapActions: () -> Unit = {},
) {
    val settings = koinInject<AppSettings>()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.reader_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            run {
                var readerMode by remember { mutableStateOf(settings.defaultReaderMode) }
                SettingsSingleChoice(
                    title = stringResource(Res.string.default_mode),
                    options = listOf(
                        stringResource(Res.string.standard) to ReaderMode.STANDARD,
                        stringResource(Res.string.right_to_left) to ReaderMode.REVERSED,
                        stringResource(Res.string.vertical) to ReaderMode.VERTICAL,
                        stringResource(Res.string.webtoon) to ReaderMode.WEBTOON,
                    ),
                    selected = readerMode,
                    onSelect = { settings.defaultReaderMode = it; readerMode = it },
                )
            }
            BoolPref(settings, "reader_mode_detect", stringResource(Res.string.detect_reader_mode), stringResource(Res.string.detect_reader_mode_summary), true)
            IndexListPref(
                settings, "zoom_mode", stringResource(Res.string.scale_mode),
                listOf(stringResource(Res.string.zoom_mode_fit_center), stringResource(Res.string.zoom_mode_fit_height), stringResource(Res.string.zoom_mode_fit_width), stringResource(Res.string.zoom_mode_keep_start)), 0,
            )
            BoolPref(settings, "reader_zoom_buttons", stringResource(Res.string.reader_zoom_buttons), stringResource(Res.string.reader_zoom_buttons_summary), false)
            BoolPref(settings, "webtoon_zoom", stringResource(Res.string.webtoon_zoom), stringResource(Res.string.webtoon_zoom_summary), true)
            run {
                var v by remember { mutableStateOf(settings.prefInt("webtoon_zoom_out", 0)) }
                SettingsSlider(
                    title = stringResource(Res.string.default_webtoon_zoom_out),
                    value = v, valueRange = 0..50, step = 10, valueLabel = "$v%",
                    onValueChange = { settings.setPref("webtoon_zoom_out", it); v = it },
                )
            }
            BoolPref(settings, "webtoon_gaps", stringResource(Res.string.webtoon_gaps), stringResource(Res.string.webtoon_gaps_summary), false)
            MultiPref(
                settings, "reader_controls", stringResource(Res.string.reader_controls_in_bottom_bar),
                listOf(
                    stringResource(Res.string.prev_chapter) to "0", stringResource(Res.string.next_chapter) to "1",
                    stringResource(Res.string.pages_slider) to "2", stringResource(Res.string.chapters_and_pages) to "3",
                    stringResource(Res.string.screen_orientation) to "4", stringResource(Res.string.save_page) to "5",
                    stringResource(Res.string.automatic_scroll) to "6", stringResource(Res.string.bookmark_add) to "7",
                ),
            )
            SettingsItem(title = stringResource(Res.string.reader_actions), onClick = onTapActions)
            BoolPref(settings, "reader_taps_ltr", stringResource(Res.string.reader_control_ltr), stringResource(Res.string.reader_control_ltr_summary), false)
            BoolPref(settings, "reader_volume_buttons", stringResource(Res.string.switch_pages_volume_buttons), stringResource(Res.string.switch_pages_volume_buttons_summary), false)
            BoolPref(settings, "reader_navigation_inverted", stringResource(Res.string.reader_navigation_inverted), stringResource(Res.string.reader_navigation_inverted_summary), false)
            IndexListPref(
                settings, "reader_animation2", stringResource(Res.string.pages_animation),
                listOf(stringResource(Res.string.disabled), stringResource(Res.string.system_default), stringResource(Res.string.advanced)), 1,
            )
            BoolPref(settings, "webtoon_pull_gesture", stringResource(Res.string.enable_pull_gesture_title), stringResource(Res.string.enable_pull_gesture_summary), false)
            BoolPref(settings, "enhanced_colors", stringResource(Res.string.enhanced_colors), stringResource(Res.string.enhanced_colors_summary), false)
            BoolPref(settings, "reader_optimize", stringResource(Res.string.reader_optimize), stringResource(Res.string.reader_optimize_summary), false)
            MultiPref(
                settings, "reader_crop", stringResource(Res.string.crop_pages),
                listOf(stringResource(Res.string.pages) to "pages", stringResource(Res.string.webtoon) to "webtoon"),
            )
            BoolPref(settings, "reader_fullscreen", stringResource(Res.string.fullscreen_mode), stringResource(Res.string.reader_fullscreen_summary), true)
            IndexListPref(
                settings, "reader_orientation", stringResource(Res.string.screen_orientation),
                listOf(stringResource(Res.string.system_default), stringResource(Res.string.automatic), stringResource(Res.string.portrait), stringResource(Res.string.landscape)), 0,
            )
            BoolPref(settings, "reader_screen_on", stringResource(Res.string.keep_screen_on), stringResource(Res.string.keep_screen_on_summary), true)
            BoolPref(settings, "reader_multitask", stringResource(Res.string.reader_multitask), stringResource(Res.string.reader_multitask_summary), false)
            BoolPref(settings, "reader_bar", stringResource(Res.string.reader_info_bar), stringResource(Res.string.reader_info_bar_summary), false)
            BoolPref(settings, "reader_bar_transparent", stringResource(Res.string.reader_info_bar_transparent), null, true)
            BoolPref(settings, "reader_chapter_toast", stringResource(Res.string.reader_chapter_toast), stringResource(Res.string.reader_chapter_toast_summary), true)
            IndexListPref(
                settings, "reader_background", stringResource(Res.string.background),
                listOf(stringResource(Res.string.system_default), stringResource(Res.string.color_light), stringResource(Res.string.color_dark), stringResource(Res.string.color_white), stringResource(Res.string.color_black)), 0,
            )
            BoolPref(settings, "pages_numbers", stringResource(Res.string.show_pages_numbers), stringResource(Res.string.show_pages_numbers_summary), false)
            IndexListPref(
                settings, "pages_preload", stringResource(Res.string.preload_pages),
                listOf(stringResource(Res.string.always), stringResource(Res.string.only_using_wifi), stringResource(Res.string.never)), 2,
            )
        }
    }
}
