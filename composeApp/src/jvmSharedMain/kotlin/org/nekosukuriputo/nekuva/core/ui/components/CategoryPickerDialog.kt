package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.nekosukuriputo.nekuva.core.model.FavouriteCategory
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.cancel
import nekuva.composeapp.generated.resources.categories
import nekuva.composeapp.generated.resources.default_category
import nekuva.composeapp.generated.resources.done

/**
 * Multi-select category picker (Doki FavoriteDialog): pick which favourite categories the selected manga
 * go into. Includes the Default category (id 0). [onConfirm] receives the checked category ids.
 */
@Composable
fun CategoryPickerDialog(
    categories: List<FavouriteCategory>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    preselected: Set<Long> = emptySet(),
) {
    var checked by remember { mutableStateOf(preselected) }
    val defaultLabel = stringResource(Res.string.default_category)
    val rows = remember(categories) {
        buildList {
            add(0L to defaultLabel)
            categories.forEach { add(it.id to it.title) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.categories)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                rows.forEach { (id, label) ->
                    val isChecked = id in checked
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { checked = if (isChecked) checked - id else checked + id }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = { checked = if (it) checked + id else checked - id })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked); onDismiss() }, enabled = checked.isNotEmpty()) {
                Text(stringResource(Res.string.done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
