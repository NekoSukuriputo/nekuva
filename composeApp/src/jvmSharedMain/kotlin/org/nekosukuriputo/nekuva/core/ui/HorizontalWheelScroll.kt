package org.nekosukuriputo.nekuva.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Desktop: lets a horizontal row (LazyRow / `Modifier.horizontalScroll`) be scrolled with the vertical
 * mouse-wheel or trackpad — Compose desktop otherwise only scrolls vertical containers with the wheel, so
 * the filter-chip rows (History / source / Local) couldn't be scrolled with a mouse. No-op on Android
 * (touch already scrolls horizontally). Pass the row's [ScrollableState] (LazyListState or ScrollState).
 */
@Composable
expect fun Modifier.horizontalWheelScroll(state: ScrollableState): Modifier
