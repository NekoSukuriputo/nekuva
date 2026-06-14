package org.nekosukuriputo.nekuva.reader.data

import com.russhwolf.settings.ObservableSettings
import org.nekosukuriputo.nekuva.reader.domain.TapGridArea
import org.nekosukuriputo.nekuva.reader.domain.TapAction

/**
 * Per-zone tap / long-tap action store (KMP port of Doki's TapGridSettings — was Android
 * SharedPreferences, here the shared [ObservableSettings]). Seeds Doki's default layout on first use.
 */
class TapGridSettings(
	private val prefs: ObservableSettings,
) {

	init {
		if (!prefs.getBoolean(KEY_INIT, false)) reset()
	}

	fun getTapAction(area: TapGridArea, isLongTap: Boolean): TapAction? {
		val raw = prefs.getStringOrNull(key(area, isLongTap)) ?: return null
		return TapAction.entries.find { it.name == raw }
	}

	fun setTapAction(area: TapGridArea, isLongTap: Boolean, action: TapAction?) {
		val k = key(area, isLongTap)
		if (action == null) prefs.remove(k) else prefs.putString(k, action.name)
	}

	/** Restore Doki's default layout. */
	fun reset() {
		clearAll()
		set(TapGridArea.TOP_LEFT, TapAction.PAGE_PREV)
		set(TapGridArea.TOP_CENTER, TapAction.PAGE_PREV)
		set(TapGridArea.CENTER_LEFT, TapAction.PAGE_PREV)
		set(TapGridArea.BOTTOM_LEFT, TapAction.PAGE_PREV)
		set(TapGridArea.CENTER, TapAction.TOGGLE_UI)
		setLong(TapGridArea.CENTER, TapAction.SHOW_MENU)
		set(TapGridArea.TOP_RIGHT, TapAction.PAGE_NEXT)
		set(TapGridArea.CENTER_RIGHT, TapAction.PAGE_NEXT)
		set(TapGridArea.BOTTOM_CENTER, TapAction.PAGE_NEXT)
		set(TapGridArea.BOTTOM_RIGHT, TapAction.PAGE_NEXT)
		prefs.putBoolean(KEY_INIT, true)
	}

	/** Disable every zone (Doki's disableAll). */
	fun disableAll() {
		clearAll()
		prefs.putBoolean(KEY_INIT, true)
	}

	private fun clearAll() {
		for (area in TapGridArea.entries) {
			prefs.remove(key(area, false))
			prefs.remove(key(area, true))
		}
	}

	private fun set(area: TapGridArea, action: TapAction) = prefs.putString(key(area, false), action.name)
	private fun setLong(area: TapGridArea, action: TapAction) = prefs.putString(key(area, true), action.name)

	private fun key(area: TapGridArea, isLongTap: Boolean) =
		"tap_grid_" + area.name + if (isLongTap) "_long" else ""

	private companion object {
		const val KEY_INIT = "tap_grid_init"
	}
}
