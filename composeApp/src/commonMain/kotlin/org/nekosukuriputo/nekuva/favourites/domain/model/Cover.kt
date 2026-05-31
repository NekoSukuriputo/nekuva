package org.nekosukuriputo.nekuva.favourites.domain.model

import org.nekosukuriputo.nekuva.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
