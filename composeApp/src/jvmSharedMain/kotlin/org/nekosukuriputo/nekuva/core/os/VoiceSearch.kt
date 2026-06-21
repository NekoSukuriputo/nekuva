package org.nekosukuriputo.nekuva.core.os

import androidx.compose.runtime.Composable

/**
 * Voice search (Doki VoiceInputContract): returns a launcher that starts the platform speech recognizer and
 * delivers the recognized text to [onResult], or null when voice input isn't available (Desktop, or no
 * recognizer installed) so callers can hide the mic button.
 */
@Composable
expect fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): (() -> Unit)?
