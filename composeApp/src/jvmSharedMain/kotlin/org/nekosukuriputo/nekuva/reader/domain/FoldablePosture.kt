package org.nekosukuriputo.nekuva.reader.domain

import androidx.compose.runtime.Composable

/**
 * Whether the device is currently in a foldable "book" posture (half-opened, vertical hinge) — Doki's
 * `auto_double_foldable`: the reader switches to two-page automatically. Android observes
 * `WindowInfoTracker`; Desktop has no foldable concept (always false).
 */
@Composable
expect fun rememberIsBookPosture(): Boolean
