package org.nekosukuriputo.nekuva.core.security

// Desktop has no biometric API — the app lock falls back to the password only.
actual fun isBiometricAvailable(): Boolean = false

actual suspend fun authenticateBiometric(title: String, subtitle: String): Boolean = false
