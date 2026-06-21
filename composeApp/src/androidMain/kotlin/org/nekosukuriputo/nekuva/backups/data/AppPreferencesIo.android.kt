package org.nekosukuriputo.nekuva.backups.data

import android.content.Context
import org.koin.core.context.GlobalContext

private fun prefs() =
    GlobalContext.get().get<Context>().getSharedPreferences("nekuva_prefs", Context.MODE_PRIVATE)

actual fun dumpAppPreferences(): Map<String, Any?> = prefs().all

actual fun writeAppPreferences(values: Map<String, Any?>) {
    val editor = prefs().edit()
    for ((key, value) in values) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Double -> editor.putFloat(key, value.toFloat()) // SharedPreferences has no Double
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            is Collection<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    editor.apply()
}
