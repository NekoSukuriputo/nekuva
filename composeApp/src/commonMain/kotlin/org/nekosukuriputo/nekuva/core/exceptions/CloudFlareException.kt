package org.nekosukuriputo.nekuva.core.exceptions

import okio.IOException
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

abstract class CloudFlareException(
	message: String,
	val state: Int,
) : IOException(message) {

	abstract val url: String

	abstract val source: MangaSource
}
