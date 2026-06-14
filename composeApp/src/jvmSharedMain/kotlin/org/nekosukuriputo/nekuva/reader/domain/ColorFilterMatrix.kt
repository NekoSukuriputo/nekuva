package org.nekosukuriputo.nekuva.reader.domain

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Convert the reader colour filter into a Compose [ColorFilter]. KMP port of Doki's
 * `ReaderColorFilter.toColorFilter()` (which built an android.graphics.ColorMatrix) onto Compose's
 * [ColorMatrix]. Returns null when nothing is set, so callers can skip the filter entirely.
 *
 * Compose's ColorMatrix mirrors android.graphics.ColorMatrix semantics (4x5, offsets in 0..255),
 * so the brightness/contrast/invert/grayscale/book math matches Doki's.
 */
fun ReaderColorFilter.toComposeColorFilter(): ColorFilter? {
	if (isEmpty) return null
	val cm = ColorMatrix()
	if (isGrayscale) {
		cm.setToSaturation(0f)
	}
	if (isInverted) {
		cm.timesAssign(
			ColorMatrix(
				floatArrayOf(
					-1f, 0f, 0f, 0f, 255f,
					0f, -1f, 0f, 0f, 255f,
					0f, 0f, -1f, 0f, 255f,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	if (brightness != 0f) {
		val scale = brightness + 1f
		cm.timesAssign(
			ColorMatrix(
				floatArrayOf(
					scale, 0f, 0f, 0f, 0f,
					0f, scale, 0f, 0f, 0f,
					0f, 0f, scale, 0f, 0f,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	if (contrast != 0f) {
		val scale = contrast + 1f
		val translate = (-0.5f * scale + 0.5f) * 255f
		cm.timesAssign(
			ColorMatrix(
				floatArrayOf(
					scale, 0f, 0f, 0f, translate,
					0f, scale, 0f, 0f, translate,
					0f, 0f, scale, 0f, translate,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	if (isBookBackground) {
		cm.timesAssign(
			ColorMatrix(
				floatArrayOf(
					1f, 0f, 0f, 0f, 0f,
					0f, 1f, 0f, 0f, 0f,
					0f, 0f, BOOK_BLUE_FACTOR, 0f, 0f,
					0f, 0f, 0f, 1f, 0f,
				),
			),
		)
	}
	return ColorFilter.colorMatrix(cm)
}

private const val BOOK_BLUE_FACTOR = 0.92f
