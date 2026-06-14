package org.nekosukuriputo.nekuva.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Toggle the platform "secure window" flag (Android FLAG_SECURE — blocks screenshots/recents preview).
 *  Desktop has no equivalent (no-op). */
expect fun applySecureFlag(secure: Boolean)

/** Reference-counts secure requests so multiple screens can ask independently (Doki ScreenshotPolicyHelper). */
private object ScreenSecurityState {
    private var count = 0
    fun acquire() {
        count++
        if (count == 1) applySecureFlag(true)
    }
    fun release() {
        if (count > 0) count--
        if (count == 0) applySecureFlag(false)
    }
}

/** While [secure] is true and this composable is in the tree, the window blocks screenshots. */
@Composable
fun SecureScreenEffect(secure: Boolean) {
    DisposableEffect(secure) {
        if (secure) ScreenSecurityState.acquire()
        onDispose { if (secure) ScreenSecurityState.release() }
    }
}
