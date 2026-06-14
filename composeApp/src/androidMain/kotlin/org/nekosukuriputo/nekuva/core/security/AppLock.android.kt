package org.nekosukuriputo.nekuva.core.security

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import kotlin.coroutines.resume

// Uses the framework BiometricPrompt (API 28+) so no androidx.biometric dependency / FragmentActivity
// is required. On API < 28 (minSdk 24) biometrics are simply unavailable -> password only.

actual fun isBiometricAvailable(): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val ctx = LocaleActivityHolder.current?.get() ?: return false
    val manager = ctx.getSystemService(BiometricManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    return manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
}

actual suspend fun authenticateBiometric(title: String, subtitle: String): Boolean {
    if (Build.VERSION.SDK_INT < 28) return false
    val activity = LocaleActivityHolder.current?.get() ?: return false
    return suspendCancellableCoroutine { cont ->
        val cancellation = CancellationSignal()
        cont.invokeOnCancellation { cancellation.cancel() }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(
                activity.getString(android.R.string.cancel),
                activity.mainExecutor,
                { _, _ -> if (cont.isActive) cont.resume(false) },
            )
            .build()
        prompt.authenticate(
            cancellation,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // Wrong finger/face — keep the prompt open; do not resume.
                }
            },
        )
    }
}
