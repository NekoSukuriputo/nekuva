package org.nekosukuriputo.nekuva.core.model

import org.nekosukuriputo.nekuva.parsers.model.MangaSource

data class MangaSourceInfo(
    val mangaSource: MangaSource,
    val isEnabled: Boolean,
    val isPinned: Boolean,
) : MangaSource by mangaSource
