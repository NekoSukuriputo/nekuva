package org.nekosukuriputo.nekuva.core.i18n

import java.util.Locale

// Captured once at startup so "follow system" can be restored after a manual override.
private val systemDefaultLocale: Locale = Locale.getDefault()

actual fun applyAppLocale(tag: String) {
    Locale.setDefault(if (tag.isEmpty()) systemDefaultLocale else Locale.forLanguageTag(tag))
}

// Desktop has no Activity to recreate; App() re-keys on the locale tag, which re-resolves resources.
actual fun recreateForLocale() = Unit
