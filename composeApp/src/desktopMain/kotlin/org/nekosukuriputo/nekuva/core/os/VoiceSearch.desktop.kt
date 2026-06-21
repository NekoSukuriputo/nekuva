package org.nekosukuriputo.nekuva.core.os

import androidx.compose.runtime.Composable

// Desktop has no system speech recognizer → no voice search (hide the mic button).
@Composable
actual fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): (() -> Unit)? = null
