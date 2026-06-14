package org.nekosukuriputo.nekuva.reader.domain

import android.graphics.Bitmap
import kotlin.math.abs

private const val THRESHOLD = 0x10   // per-channel tolerance (white/black-ish margins)
private const val STRIDE = 4         // sample every Nth pixel along a scan line

/** Android crop: bulk-read pixels once, scan edges inward, then crop with [Bitmap.createBitmap]. */
actual fun trimImageBorders(bitmap: Bitmap): Bitmap {
	val w = bitmap.width
	val h = bitmap.height
	if (w < 8 || h < 8) return bitmap
	val pixels = try {
		IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
	} catch (e: Exception) {
		return bitmap // e.g. a hardware bitmap with no CPU-accessible pixels
	}
	val bg = pixels[0]

	fun rowUniform(y: Int): Boolean {
		val base = y * w
		var x = 0
		while (x < w) {
			if (!colorClose(pixels[base + x], bg)) return false
			x += STRIDE
		}
		return true
	}
	fun colUniform(x: Int): Boolean {
		var y = 0
		while (y < h) {
			if (!colorClose(pixels[y * w + x], bg)) return false
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
	return try {
		Bitmap.createBitmap(bitmap, left, top, nw, nh)
	} catch (e: Exception) {
		bitmap
	}
}

private fun colorClose(a: Int, b: Int): Boolean =
	abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)) <= THRESHOLD &&
		abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)) <= THRESHOLD &&
		abs((a and 0xFF) - (b and 0xFF)) <= THRESHOLD
