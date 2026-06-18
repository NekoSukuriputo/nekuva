package org.nekosukuriputo.nekuva.core.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Reusable multi-select state (Doki ActionMode parity), keyed by item id. Long-press enters selection;
 * tap toggles while active. Shared by History/Favourites/Local (Long ids) and Downloads (String ids).
 */
class SelectionState<K> {
    var selected by mutableStateOf<Set<K>>(emptySet())
        private set

    val isActive: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size

    fun isSelected(id: K): Boolean = id in selected

    fun toggle(id: K) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll(ids: Collection<K>) {
        selected = selected + ids
    }

    fun clear() {
        selected = emptySet()
    }
}

@Composable
fun <K> rememberSelectionState(): SelectionState<K> = remember { SelectionState() }
