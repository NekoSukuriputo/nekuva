package org.nekosukuriputo.nekuva.core.exceptions

import org.nekosukuriputo.nekuva.parsers.model.Manga

class UnsupportedSourceException(
	message: String?,
	val manga: Manga?,
) : IllegalArgumentException(message)
