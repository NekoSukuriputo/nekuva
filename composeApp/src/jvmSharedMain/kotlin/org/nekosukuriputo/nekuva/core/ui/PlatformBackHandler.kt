package org.nekosukuriputo.nekuva.core.ui

import androidx.compose.runtime.Composable

/** System-back interceptor. Android uses the activity back dispatcher; Desktop has no system back (no-op). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
