package org.nekosukuriputo.nekuva.core.os

import androidx.compose.runtime.Composable

/**
 * Returns an action that opens the OS "ignore battery optimizations" prompt (Doki `ignore_dose`), or null
 * when the concept doesn't apply to the platform (Desktop) — in which case the settings row is hidden.
 */
@Composable
expect fun rememberBatteryOptimizationRequest(): (() -> Unit)?
