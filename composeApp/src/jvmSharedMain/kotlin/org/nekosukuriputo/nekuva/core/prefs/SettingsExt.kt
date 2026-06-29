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

fun Settings.getSafeStringSet(key: String, defaultValue: Set<String>): Set<String> {
    val str = try {
        getStringOrNull(key)
    } catch (e: Exception) {
        // Catch ClassCastException if the user upgraded from legacy Doki which used native StringSet
        null
    }
    if (str.isNullOrBlank()) return defaultValue
    if (str == "##EMPTY##") return emptySet()
    return str.split(",").filter { it.isNotEmpty() }.toSet()
}

fun Settings.putSafeStringSet(key: String, value: Set<String>) {
    if (value.isEmpty()) {
        putString(key, "##EMPTY##")
    } else {
        putString(key, value.joinToString(","))
    }
}

fun String?.nullIfEmpty(): String? = if (this.isNullOrEmpty()) null else this
