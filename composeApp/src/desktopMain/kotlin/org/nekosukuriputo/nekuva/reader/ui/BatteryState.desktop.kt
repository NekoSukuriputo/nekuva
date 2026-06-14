package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable

/** Desktop: no battery info in the reader bar. */
@Composable
actual fun rememberBatteryPercent(): Int? = null
