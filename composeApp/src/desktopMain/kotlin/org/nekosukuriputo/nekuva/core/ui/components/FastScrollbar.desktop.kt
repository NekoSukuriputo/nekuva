package org.nekosukuriputo.nekuva.core.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

val customScrollbarStyle
    @Composable get() = ScrollbarStyle(
        minimalHeight = 32.dp,
        thickness = 13.dp,
        shape = RoundedCornerShape(8.dp),
        hoverDurationMillis = 300,
        unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )

@Composable
actual fun FastScrollbar(
    state: LazyGridState,
    modifier: Modifier
) {
    VerticalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(state),
        style = customScrollbarStyle
    )
}

@Composable
actual fun FastScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier
) {
    VerticalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(state),
        style = customScrollbarStyle
    )
}
