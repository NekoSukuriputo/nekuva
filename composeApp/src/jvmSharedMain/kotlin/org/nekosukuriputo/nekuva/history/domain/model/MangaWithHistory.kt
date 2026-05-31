package org.nekosukuriputo.nekuva.history.domain.model

import org.nekosukuriputo.nekuva.core.model.MangaHistory
import org.nekosukuriputo.nekuva.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
