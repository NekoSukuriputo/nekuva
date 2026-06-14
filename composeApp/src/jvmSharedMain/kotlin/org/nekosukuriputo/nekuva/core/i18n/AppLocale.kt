package org.nekosukuriputo.nekuva.core.i18n

/**
 * Apply [tag] (BCP-47; "" = follow system) so Compose Resources re-resolve strings. Desktop sets the
 * JVM default Locale (App() re-keys to recompose); Android applies via the Activity Configuration +
 * recreate (see [recreateForLocale] and MainActivity.attachBaseContext), so this is a no-op there
 * beyond keeping `Locale.getDefault()` consistent.
 */
expect fun applyAppLocale(tag: String)

/** Recreate the host so a new locale takes effect — Android recreates the Activity; Desktop is a no-op. */
expect fun recreateForLocale()

/** Self-localized display name for a language [tag] (e.g. "id" -> "Bahasa Indonesia"); "" -> "". */
fun localeDisplayName(tag: String): String {
    if (tag.isEmpty()) return ""
    val locale = java.util.Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(locale).ifBlank { tag }
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
