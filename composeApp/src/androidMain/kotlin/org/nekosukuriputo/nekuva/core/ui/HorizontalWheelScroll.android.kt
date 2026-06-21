package org.nekosukuriputo.nekuva.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android: touch already scrolls horizontal rows — no extra behavior needed. */
@Composable
actual fun Modifier.horizontalWheelScroll(state: ScrollableState): Modifier = this
