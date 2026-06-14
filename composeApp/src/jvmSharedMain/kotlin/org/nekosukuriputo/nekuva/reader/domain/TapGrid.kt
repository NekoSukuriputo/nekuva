package org.nekosukuriputo.nekuva.reader.domain

import kotlin.math.roundToInt

/** The 9 tappable zones of the reader (Doki's TapGridArea). */
enum class TapGridArea {
	TOP_LEFT, TOP_CENTER, TOP_RIGHT,
	CENTER_LEFT, CENTER, CENTER_RIGHT,
	BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

/** Action assignable to a tap/long-tap on a zone (Doki's TapAction). */
enum class TapAction {
	PAGE_NEXT, PAGE_PREV, CHAPTER_NEXT, CHAPTER_PREV, TOGGLE_UI, SHOW_MENU,
}

/** Resolve which grid zone a tap at ([x],[y]) within a [width]x[height] surface falls into (Doki). */
fun tapGridAreaAt(x: Float, y: Float, width: Int, height: Int): TapGridArea? {
	if (width <= 0 || height <= 0) return null
	val xi = (x * 2f / width).roundToInt().coerceIn(0, 2)
	val yi = (y * 2f / height).roundToInt().coerceIn(0, 2)
	return when (xi) {
		0 -> when (yi) { 0 -> TapGridArea.TOP_LEFT; 1 -> TapGridArea.CENTER_LEFT; else -> TapGridArea.BOTTOM_LEFT }
		1 -> when (yi) { 0 -> TapGridArea.TOP_CENTER; 1 -> TapGridArea.CENTER; else -> TapGridArea.BOTTOM_CENTER }
		else -> when (yi) { 0 -> TapGridArea.TOP_RIGHT; 1 -> TapGridArea.CENTER_RIGHT; else -> TapGridArea.BOTTOM_RIGHT }
	}
}
