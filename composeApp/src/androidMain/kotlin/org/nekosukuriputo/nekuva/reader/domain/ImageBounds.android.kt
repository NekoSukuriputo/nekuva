package org.nekosukuriputo.nekuva.reader.domain

import android.graphics.BitmapFactory

actual fun decodeImageBounds(bytes: ByteArray): Pair<Int, Int>? {
	val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
	BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
	return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
}
