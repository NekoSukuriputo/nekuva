package org.nekosukuriputo.nekuva.core.prefs

import com.russhwolf.settings.Settings

inline fun <reified T : Enum<T>> Settings.getEnum(key: String, defaultValue: T): T {
    val name = getStringOrNull(key) ?: return defaultValue
    return try {
        enumValueOf<T>(name)
    } catch (e: Exception) {
        defaultValue
    }
}

inline fun <reified T : Enum<T>> Settings.putEnum(key: String, value: T) {
    putString(key, value.name)
}

fun Settings.getStringSet(key: String, defaultValue: Set<String>): Set<String> {
    val str = getStringOrNull(key) ?: return defaultValue
    return str.split(",").filter { it.isNotEmpty() }.toSet()
}

fun Settings.putStringSet(key: String, value: Set<String>) {
    putString(key, value.joinToString(","))
}

fun String?.nullIfEmpty(): String? = if (this.isNullOrEmpty()) null else this
