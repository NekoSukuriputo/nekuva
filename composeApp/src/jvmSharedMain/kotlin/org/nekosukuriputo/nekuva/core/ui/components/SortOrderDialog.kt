package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.list.domain.ListSortOrder
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.*

/**
 * Reusable sort-order picker (Doki library sort sheet) for `ListSortOrder` lists — History/Favourites.
 * Optional "group by date" toggle ([grouping] = null hides it).
 */
@Composable
fun SortOrderDialog(
    current: ListSortOrder,
    options: Collection<ListSortOrder>,
    onSelect: (ListSortOrder) -> Unit,
    onDismiss: () -> Unit,
    grouping: Boolean? = null,
    onGroupingChange: (Boolean) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sort_order)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                options.forEach { order ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(order) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = order == current, onClick = { onSelect(order) })
                        Spacer(Modifier.width(8.dp))
                        Text(sortLabel(order))
                    }
                }
                if (grouping != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onGroupingChange(!grouping) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.group_by_date), modifier = Modifier.weight(1f))
                        Switch(checked = grouping, onCheckedChange = { onGroupingChange(it) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.done)) } },
    )
}

/** Localized label for a [ListSortOrder] (Doki sort labels). */
@Composable
fun sortLabel(order: ListSortOrder): String = stringResource(
    when (order) {
        ListSortOrder.LAST_READ -> Res.string.last_read
        ListSortOrder.LONG_AGO_READ -> Res.string.long_ago_read
        ListSortOrder.NEWEST -> Res.string.newest
        ListSortOrder.OLDEST -> Res.string.oldest
        ListSortOrder.PROGRESS -> Res.string.progress
        ListSortOrder.UNREAD -> Res.string.unread
        ListSortOrder.ALPHABETIC -> Res.string.by_name
        ListSortOrder.ALPHABETIC_REVERSE -> Res.string.by_name_reverse
        ListSortOrder.NEW_CHAPTERS -> Res.string.new_chapters
        ListSortOrder.UPDATED -> Res.string.updated
        ListSortOrder.RATING -> Res.string.by_rating
        ListSortOrder.RELEVANCE -> Res.string.by_relevance
    },
)
