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
import androidx.compose.ui.Modifier
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.appearance
import nekuva.composeapp.generated.resources.black_dark_theme
import nekuva.composeapp.generated.resources.black_dark_theme_summary
import nekuva.composeapp.generated.resources.dark
import nekuva.composeapp.generated.resources.follow_system
import nekuva.composeapp.generated.resources.light
import nekuva.composeapp.generated.resources.theme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSingleChoice
import org.nekosukuriputo.nekuva.settings.ui.components.SettingsSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: AppearanceViewModel = koinViewModel(),
    onBackClick: () -> Unit,
) {
    val themeMode by viewModel.theme.collectAsState()
    val amoled by viewModel.amoled.collectAsState()

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
            SettingsSingleChoice(
                title = stringResource(Res.string.theme),
                options = listOf(
                    stringResource(Res.string.follow_system) to -1,
                    stringResource(Res.string.light) to 1,
                    stringResource(Res.string.dark) to 2,
                ),
                selected = themeMode,
                onSelect = { viewModel.setTheme(it) },
            )
            SettingsSwitch(
                title = stringResource(Res.string.black_dark_theme),
                summary = stringResource(Res.string.black_dark_theme_summary),
                checked = amoled,
                onCheckedChange = { viewModel.setAmoled(it) },
            )
        }
    }
}
