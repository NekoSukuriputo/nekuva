package org.nekosukuriputo.nekuva.core.prefs

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import org.nekosukuriputo.nekuva.parsers.config.ConfigKey
import org.nekosukuriputo.nekuva.parsers.config.MangaSourceConfig
import org.nekosukuriputo.nekuva.parsers.model.MangaSource
import org.nekosukuriputo.nekuva.parsers.model.SortOrder
import org.nekosukuriputo.nekuva.parsers.util.ifNullOrEmpty
import org.nekosukuriputo.nekuva.parsers.util.nullIfEmpty

class SourceSettings(source: MangaSource, private val prefs: ObservableSettings) : MangaSourceConfig {

	var defaultSortOrder: SortOrder?
		get() {
			val name = prefs.getStringOrNull(KEY_SORT_ORDER) ?: return null
			return try { enumValueOf<SortOrder>(name) } catch (e: Exception) { null }
		}
		set(value) {
			if (value != null) prefs.putString(KEY_SORT_ORDER, value.name) else prefs.remove(KEY_SORT_ORDER)
		}

	var isSlowdownEnabled: Boolean
		get() = prefs.getBoolean(KEY_SLOWDOWN, false)
		set(value) = prefs.putBoolean(KEY_SLOWDOWN, value)

	var isCaptchaNotificationsDisabled: Boolean
		get() = prefs.getBoolean(KEY_NO_CAPTCHA, false)
		set(value) = prefs.putBoolean(KEY_NO_CAPTCHA, value)

	@Suppress("UNCHECKED_CAST")
	override fun <T> get(key: ConfigKey<T>): T {
		return when (key) {
			is ConfigKey.UserAgent -> (prefs.getStringOrNull(key.key) ?: key.defaultValue)
				.ifNullOrEmpty { key.defaultValue }

			is ConfigKey.Domain -> (prefs.getStringOrNull(key.key) ?: key.defaultValue).trim()

			is ConfigKey.ShowSuspiciousContent -> prefs.getBoolean(key.key, key.defaultValue)
			is ConfigKey.SplitByTranslations -> prefs.getBoolean(key.key, key.defaultValue)
			is ConfigKey.PreferredImageServer -> prefs.getStringOrNull(key.key)?.nullIfEmpty() ?: key.defaultValue
		} as T
	}

	operator fun <T> set(key: ConfigKey<T>, value: T) {
		when (key) {
			is ConfigKey.Domain -> prefs.putString(key.key, value as String)
			is ConfigKey.ShowSuspiciousContent -> prefs.putBoolean(key.key, value as Boolean)
			is ConfigKey.UserAgent -> prefs.putString(key.key, value as String)
			is ConfigKey.SplitByTranslations -> prefs.putBoolean(key.key, value as Boolean)
			is ConfigKey.PreferredImageServer -> prefs.putString(key.key, value as String? ?: "")
		}
	}

	companion object {
		const val KEY_DOMAIN = "domain"
		const val KEY_NO_CAPTCHA = "no_captcha"
		const val KEY_SLOWDOWN = "slowdown"
		const val KEY_SORT_ORDER = "sort_order"
	}
}
