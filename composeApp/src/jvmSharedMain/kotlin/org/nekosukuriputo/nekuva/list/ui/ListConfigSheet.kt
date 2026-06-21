package org.nekosukuriputo.nekuva.list.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.prefs.ListMode

/**
 * Doki's "list options" sheet (`ListConfigSheet`): pick the list display mode (Grid / List / Detailed)
 * for a given section ([listModeKey] = global or a per-screen key) plus the grid cell size. Writes are
 * applied live — every list screen observes these settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListConfigSheet(
    settings: AppSettings,
    listModeKey: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(settings.getListMode(listModeKey)) }
    var gridSize by remember { mutableIntStateOf(settings.gridSize) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Scrollable: History/Favourites add a sort list that overflows the sheet on small screens —
        // without this the last sort options are clipped at the screen bottom (looked like a stray radio).
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(Res.string.list_options),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(Res.string.list_mode),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val options = listOf(
                stringResource(Res.string.list) to ListMode.LIST,
                stringResource(Res.string.detailed_list) to ListMode.DETAILED_LIST,
                stringResource(Res.string.grid) to ListMode.GRID,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                options.forEachIndexed { index, (label, value) ->
                    SegmentedButton(
                        selected = value == mode,
                        onClick = { settings.setListMode(listModeKey, value); mode = value },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            if (mode == ListMode.GRID) {
                Text(
                    text = stringResource(Res.string.grid_size) + "  $gridSize%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                Slider(
                    value = gridSize.toFloat(),
                    valueRange = 50f..150f,
                    steps = 19, // 5% increments
                    onValueChange = { gridSize = it.toInt(); settings.gridSize = gridSize },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // History gets the "group by date" toggle + sort order (Doki ListConfigSection.History).
            if (listModeKey == AppSettings.KEY_LIST_MODE_HISTORY) {
                var grouping by remember { mutableStateOf(settings.isHistoryGroupingEnabled) }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.group_by_date), style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.material3.Switch(
                        checked = grouping,
                        onCheckedChange = { settings.isHistoryGroupingEnabled = it; grouping = it },
                    )
                }
                Text(
                    text = stringResource(Res.string.sort_order),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                var sort by remember { mutableStateOf(settings.historySortOrder) }
                org.nekosukuriputo.nekuva.list.domain.ListSortOrder.HISTORY.forEach { order ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(
                                selected = order == sort,
                                onClick = { settings.historySortOrder = order; sort = order },
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = order == sort,
                            onClick = { settings.historySortOrder = order; sort = order },
                        )
                        Text(
                            org.nekosukuriputo.nekuva.core.ui.components.sortLabel(order),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // Favourites gets the sort order (Doki ListConfigSection.Favorites). Applies to all category tabs.
            if (listModeKey == AppSettings.KEY_LIST_MODE_FAVORITES) {
                Text(
                    text = stringResource(Res.string.sort_order),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                var favSort by remember { mutableStateOf(settings.allFavoritesSortOrder) }
                org.nekosukuriputo.nekuva.list.domain.ListSortOrder.FAVORITES.forEach { order ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(
                                selected = order == favSort,
                                onClick = { settings.allFavoritesSortOrder = order; favSort = order },
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = order == favSort,
                            onClick = { settings.allFavoritesSortOrder = order; favSort = order },
                        )
                        Text(
                            org.nekosukuriputo.nekuva.core.ui.components.sortLabel(order),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
