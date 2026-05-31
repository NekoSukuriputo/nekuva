package org.nekosukuriputo.nekuva.core.exceptions

import okio.IOException
import org.nekosukuriputo.nekuva.parsers.model.MangaSource

class InteractiveActionRequiredException(
	val source: MangaSource,
	val url: String,
) : IOException("Interactive action is required for ${source.name}")
