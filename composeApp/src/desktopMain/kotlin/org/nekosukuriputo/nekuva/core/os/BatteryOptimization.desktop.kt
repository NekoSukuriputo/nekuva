package org.nekosukuriputo.nekuva.core.os

import androidx.compose.runtime.Composable

/** Desktop has no battery-optimization concept — the row is hidden. */
@Composable
actual fun rememberBatteryOptimizationRequest(): (() -> Unit)? = null
