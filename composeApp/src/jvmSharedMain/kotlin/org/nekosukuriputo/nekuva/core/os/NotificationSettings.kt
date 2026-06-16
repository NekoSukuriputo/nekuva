package org.nekosukuriputo.nekuva.core.os

import androidx.compose.runtime.Composable

/**
 * Returns an action that opens the OS app-notification settings (Doki notifications_settings: sound/
 * vibrate/light per channel), or null on platforms without notifications (Desktop) — row is hidden.
 */
@Composable
expect fun rememberNotificationSettingsRequest(): (() -> Unit)?
