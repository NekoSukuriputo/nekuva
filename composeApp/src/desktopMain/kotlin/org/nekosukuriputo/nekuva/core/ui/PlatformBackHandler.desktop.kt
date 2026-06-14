package org.nekosukuriputo.nekuva.core.ui

import androidx.compose.runtime.Composable

// Desktop has no system back button; exit-confirmation is a mobile concept.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
