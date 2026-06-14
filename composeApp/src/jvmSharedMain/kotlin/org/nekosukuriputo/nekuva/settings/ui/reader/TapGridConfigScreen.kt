package org.nekosukuriputo.nekuva.settings.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.nekosukuriputo.nekuva.reader.domain.TapAction
import org.nekosukuriputo.nekuva.reader.domain.TapGridArea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapGridConfigScreen(
    onBackClick: () -> Unit,
    viewModel: TapGridConfigViewModel = koinViewModel(),
) {
    val cells by viewModel.cells.collectAsState()
    var longMode by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.reader_actions)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.disableAll() }) { Text(stringResource(Res.string.disable_all)) }
                    TextButton(onClick = { viewModel.reset() }) { Text(stringResource(Res.string.reset)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !longMode, onClick = { longMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(Res.string.tap_action))
                }
                SegmentedButton(selected = longMode, onClick = { longMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(Res.string.long_tap_action))
                }
            }
            val byArea = cells.associateBy { it.area }
            val rows = listOf(
                listOf(TapGridArea.TOP_LEFT, TapGridArea.TOP_CENTER, TapGridArea.TOP_RIGHT),
                listOf(TapGridArea.CENTER_LEFT, TapGridArea.CENTER, TapGridArea.CENTER_RIGHT),
                listOf(TapGridArea.BOTTOM_LEFT, TapGridArea.BOTTOM_CENTER, TapGridArea.BOTTOM_RIGHT),
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { area ->
                            val cell = byArea[area]
                            val action = if (longMode) cell?.longTap else cell?.tap
                            TapCell(
                                action = action,
                                onPick = { viewModel.setAction(area, longMode, it) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TapCell(action: TapAction?, onPick: (TapAction?) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tapActionLabel(action),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val options = listOf<TapAction?>(null) + TapAction.entries
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(tapActionLabel(opt)) }, onClick = { onPick(opt); open = false })
            }
        }
    }
}

@Composable
private fun tapActionLabel(action: TapAction?): String = when (action) {
    null -> stringResource(Res.string.disabled)
    TapAction.PAGE_NEXT -> stringResource(Res.string.next_page)
    TapAction.PAGE_PREV -> stringResource(Res.string.prev_page)
    TapAction.CHAPTER_NEXT -> stringResource(Res.string.next_chapter)
    TapAction.CHAPTER_PREV -> stringResource(Res.string.prev_chapter)
    TapAction.TOGGLE_UI -> stringResource(Res.string.toggle_ui)
    TapAction.SHOW_MENU -> stringResource(Res.string.show_menu)
}
