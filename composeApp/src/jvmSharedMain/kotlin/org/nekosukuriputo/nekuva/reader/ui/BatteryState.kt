package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.runtime.Composable

/**
 * Current battery level [0..100], or null when unavailable (Desktop). Used by the reader info bar
 * (Doki's ReaderInfoBarView shows the clock + battery while reading).
 */
@Composable
expect fun rememberBatteryPercent(): Int?
