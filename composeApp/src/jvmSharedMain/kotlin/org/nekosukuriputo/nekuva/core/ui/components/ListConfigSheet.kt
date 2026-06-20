package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.prefs.ListMode
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.grid_size
import nekuva.composeapp.generated.resources.group_by_date
import nekuva.composeapp.generated.resources.list_mode
import nekuva.composeapp.generated.resources.sort_order

/**
 * Display-config bottom sheet (Doki ListConfigBottomSheet): pick the list mode (List / Detailed / Grid),
 * the grid column size (grid only), an optional "group by date" toggle, and an optional sort order.
 * Reusable across History / Favourites / Local / Suggestions list screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListConfigSheet(
    listMode: ListMode,
    onListModeChange: (ListMode) -> Unit,
    gridSize: Int,
    onGridSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    sortOrders: List<ListSortOrder>? = null,
    currentSort: ListSortOrder? = null,
    onSortChange: (ListSortOrder) -> Unit = {},
    groupingSupported: Boolean = false,
    groupingEnabled: Boolean = false,
    groupingAvailable: Boolean = true,
    onGroupingChange: (Boolean) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.list_mode), style = MaterialTheme.typography.titleSmall)
            val modes = listOf(
                ListMode.LIST to Icons.AutoMirrored.Filled.ViewList,
                ListMode.DETAILED_LIST to Icons.Filled.ViewAgenda,
                ListMode.GRID to Icons.Filled.GridView,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, icon) ->
                    SegmentedButton(
                        selected = listMode == mode,
                        onClick = { onListModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Icon(icon, contentDescription = null)
                    }
                }
            }

            if (listMode == ListMode.GRID) {
                Text(stringResource(Res.string.grid_size), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = gridSize.toFloat(),
                    onValueChange = { onGridSizeChange(it.toInt()) },
                    valueRange = 50f..160f,
                )
            }

            if (groupingSupported) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.group_by_date), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = groupingEnabled,
                        onCheckedChange = onGroupingChange,
                        enabled = groupingAvailable,
                    )
                }
            }

            if (sortOrders != null) {
                Text(stringResource(Res.string.sort_order), style = MaterialTheme.typography.titleSmall)
                sortOrders.forEach { order ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = order == currentSort,
                            onClick = { onSortChange(order) },
                        ).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = order == currentSort, onClick = { onSortChange(order) })
                        Text(sortLabel(order), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
