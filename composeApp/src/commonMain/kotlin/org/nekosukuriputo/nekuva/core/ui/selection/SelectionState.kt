package org.nekosukuriputo.nekuva.core.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Reusable multi-select state (Doki ActionMode parity), keyed by item id. Long-press enters selection;
 * tap toggles while active. Shared by History/Favourites/Local/Downloads selection modes.
 */
class SelectionState {
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set

    val isActive: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size

    fun isSelected(id: Long): Boolean = id in selected

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll(ids: Collection<Long>) {
        selected = selected + ids
    }

    fun clear() {
        selected = emptySet()
    }
}

@Composable
fun rememberSelectionState(): SelectionState = remember { SelectionState() }
