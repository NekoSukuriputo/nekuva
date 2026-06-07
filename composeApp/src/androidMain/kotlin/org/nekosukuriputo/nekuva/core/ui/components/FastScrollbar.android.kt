package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun FastScrollbar(
    state: LazyGridState,
    modifier: Modifier
) {
    // No-op on Android, LazyVerticalGrid handles its own native scrolling feel
}

@Composable
actual fun FastScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier
) {
    // No-op on Android
}
