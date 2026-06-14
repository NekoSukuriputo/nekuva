package org.nekosukuriputo.nekuva.reader.domain

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual fun decodeImageBounds(bytes: ByteArray): Pair<Int, Int>? = runCatching {
	ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { iis ->
		val readers = ImageIO.getImageReaders(iis)
		if (!readers.hasNext()) return null
		val reader = readers.next()
		try {
			reader.input = iis
			reader.getWidth(0) to reader.getHeight(0)
		} finally {
			reader.dispose()
		}
	}
}.getOrNull()
