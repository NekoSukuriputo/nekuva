package org.nekosukuriputo.nekuva.reader.domain

data class ReaderColorFilter(
	val brightness: Float,
	val contrast: Float,
	val isInverted: Boolean,
	val isGrayscale: Boolean,
	val isBookBackground: Boolean,
) {
	val isEmpty: Boolean
		get() = !isGrayscale && !isInverted && !isBookBackground && brightness == 0f && contrast == 0f

	companion object {
		val EMPTY = ReaderColorFilter(
			brightness = 0f,
			contrast = 0f,
			isInverted = false,
			isGrayscale = false,
			isBookBackground = false,
		)
	}
}
