package org.nekosukuriputo.nekuva.core.os

import androidx.compose.runtime.Composable

/** Desktop has no per-app notification channels — the row is hidden. */
@Composable
actual fun rememberNotificationSettingsRequest(): (() -> Unit)? = null
