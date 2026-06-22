package org.nekosukuriputo.nekuva.core.prefs

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings

/**
 * Wraps the app's [ObservableSettings] so a type mismatch on read never crashes the app.
 *
 * Restoring a backup from another kotatsu-lineage app (or a corrupted store) can leave a key with a
 * different type than Nekuva expects — e.g. a Boolean under a key Nekuva later reads with getString.
 * Android's SharedPreferences throws `ClassCastException` on the mismatched getter, which previously
 * crashed the app at startup (it couldn't be opened again after a restore). Each typed getter here
 * falls back to the default on `ClassCastException`, so the incompatible key is simply ignored (the
 * setting reverts to its default and is rewritten with the correct type on the next save).
 *
 * Everything else (writes, key/size/remove, change listeners) is delegated unchanged.
 */
@OptIn(ExperimentalSettingsApi::class)
class CrashSafeSettings(private val delegate: ObservableSettings) : ObservableSettings by delegate {

    override fun getString(key: String, defaultValue: String): String =
        try { delegate.getString(key, defaultValue) } catch (e: ClassCastException) { defaultValue }

    override fun getStringOrNull(key: String): String? =
        try { delegate.getStringOrNull(key) } catch (e: ClassCastException) { null }

    override fun getInt(key: String, defaultValue: Int): Int =
        try { delegate.getInt(key, defaultValue) } catch (e: ClassCastException) { defaultValue }

    override fun getIntOrNull(key: String): Int? =
        try { delegate.getIntOrNull(key) } catch (e: ClassCastException) { null }

    override fun getLong(key: String, defaultValue: Long): Long =
        try { delegate.getLong(key, defaultValue) } catch (e: ClassCastException) { defaultValue }

    override fun getLongOrNull(key: String): Long? =
        try { delegate.getLongOrNull(key) } catch (e: ClassCastException) { null }

    override fun getFloat(key: String, defaultValue: Float): Float =
        try { delegate.getFloat(key, defaultValue) } catch (e: ClassCastException) { defaultValue }

    override fun getFloatOrNull(key: String): Float? =
        try { delegate.getFloatOrNull(key) } catch (e: ClassCastException) { null }

    override fun getDouble(key: String, defaultValue: Double): Double =
        try { delegate.getDouble(key, defaultValue) } catch (e: ClassCastException) { defaultValue }

    override fun getDoubleOrNull(key: String): Double? =
        try { delegate.getDoubleOrNull(key) } catch (e: ClassCastException) { null }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try { delegate.getBoolean(key, defaultValue) } catch (e: ClassCastException) { defaultValue }

    override fun getBooleanOrNull(key: String): Boolean? =
        try { delegate.getBooleanOrNull(key) } catch (e: ClassCastException) { null }
}
