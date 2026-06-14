package org.nekosukuriputo.nekuva.core.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.lang.ref.WeakReference
import java.util.Locale

/** Tracks the foreground Activity so a locale change can recreate it (set from MainActivity). */
object LocaleActivityHolder {
    var current: WeakReference<Activity>? = null
}

actual fun applyAppLocale(tag: String) {
    // The actual UI locale on Android is driven by the Activity Configuration (localeWrap +
    // attachBaseContext). Setting the JVM default too keeps Locale.getDefault() consumers consistent.
    if (tag.isNotEmpty()) Locale.setDefault(Locale.forLanguageTag(tag))
}

actual fun recreateForLocale() {
    LocaleActivityHolder.current?.get()?.recreate()
}

/** Wrap [base] with [tag]'s locale Configuration — used by MainActivity.attachBaseContext so Compose
 *  Resources resolve to the chosen language on every API level (no AppCompat needed). */
fun localeWrap(base: Context, tag: String): Context {
    if (tag.isEmpty()) return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

/** Read the stored app-locale tag straight from prefs (needed in attachBaseContext, before Koin). */
fun storedLocaleTag(base: Context): String =
    base.getSharedPreferences("nekuva_prefs", Context.MODE_PRIVATE).getString("app_locale", "") ?: ""
