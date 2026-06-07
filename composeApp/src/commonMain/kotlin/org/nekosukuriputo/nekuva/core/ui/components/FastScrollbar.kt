package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FastScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier
)

@Composable
expect fun FastScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
)
