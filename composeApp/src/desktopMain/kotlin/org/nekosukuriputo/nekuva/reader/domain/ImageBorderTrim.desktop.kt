package org.nekosukuriputo.nekuva.reader.domain

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import kotlin.math.abs

private const val THRESHOLD = 0x10   // per-channel tolerance (white/black-ish margins)
private const val STRIDE = 4         // sample every Nth pixel along a scan line

/**
 * Desktop crop: scan edges inward with Skia's [Bitmap.getColor], then crop via [Bitmap.extractSubset]
 * — which shares the source's refcounted pixel storage, so no pixel copy is needed and the result
 * stays valid after the source bitmap is released.
 */
actual fun trimImageBorders(bitmap: Bitmap): Bitmap {
	val w = bitmap.width
	val h = bitmap.height
	if (w < 8 || h < 8) return bitmap
	val bg = bitmap.getColor(0, 0)

	fun rowUniform(y: Int): Boolean {
		var x = 0
		while (x < w) {
			if (!colorClose(bitmap.getColor(x, y), bg)) return false
			x += STRIDE
		}
		return true
	}
	fun colUniform(x: Int): Boolean {
		var y = 0
		while (y < h) {
			if (!colorClose(bitmap.getColor(x, y), bg)) return false
			y += STRIDE
		}
		return true
	}

	var top = 0
	while (top < h - 1 && rowUniform(top)) top++
	var bottom = h - 1
	while (bottom > top && rowUniform(bottom)) bottom--
	var left = 0
	while (left < w - 1 && colUniform(left)) left++
	var right = w - 1
	while (right > left && colUniform(right)) right--

	val nw = right - left + 1
	val nh = bottom - top + 1
	if (nw <= 0 || nh <= 0 || (nw >= w && nh >= h)) return bitmap
	val out = Bitmap()
	return if (bitmap.extractSubset(out, IRect.makeXYWH(left, top, nw, nh))) out else bitmap
}

private fun colorClose(a: Int, b: Int): Boolean =
	abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)) <= THRESHOLD &&
		abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)) <= THRESHOLD &&
		abs((a and 0xFF) - (b and 0xFF)) <= THRESHOLD
