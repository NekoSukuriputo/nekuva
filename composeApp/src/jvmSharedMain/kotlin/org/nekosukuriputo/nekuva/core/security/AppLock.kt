package org.nekosukuriputo.nekuva.core.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.nekosukuriputo.nekuva.core.prefs.AppSettings

/**
 * App-lock state (Doki's ProtectActivity gate). A single in-memory flag: locked when a password is set
 * and the app hasn't been unlocked yet, re-locked when the app goes to the background.
 */
object AppLockController {
    var isLocked by mutableStateOf(false)
        private set

    private var initialized = false

    /** Lock at startup if a password is set (called once from App()). */
    fun initIfNeeded(settings: AppSettings) {
        if (initialized) return
        initialized = true
        isLocked = settings.appPassword != null
    }

    /** Re-lock when leaving the foreground (called from the Activity/window lifecycle). */
    fun lockIfProtected(settings: AppSettings) {
        if (settings.appPassword != null) isLocked = true
    }

    fun unlock() {
        isLocked = false
    }
}

/** Whether the platform offers biometric auth right now (Android: enrolled fingerprint/face; else false). */
expect fun isBiometricAvailable(): Boolean

/** Show the platform biometric prompt; returns true only on a successful authentication. */
expect suspend fun authenticateBiometric(title: String, subtitle: String): Boolean
