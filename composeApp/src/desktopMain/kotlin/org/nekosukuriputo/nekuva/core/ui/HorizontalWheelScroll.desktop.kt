package org.nekosukuriputo.nekuva.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

private const val WHEEL_SCROLL_SPEED = 64f

/** Desktop: convert vertical wheel / trackpad scroll into horizontal scroll of [state]. */
@Composable
actual fun Modifier.horizontalWheelScroll(state: ScrollableState): Modifier {
    val scope = rememberCoroutineScope()
    return this.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                    // Vertical wheel (most mice) drives horizontal scroll; trackpads may report x directly.
                    val d = if (delta.y != 0f) delta.y else delta.x
                    if (d != 0f) {
                        scope.launch { state.scrollBy(d * WHEEL_SCROLL_SPEED) }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}
