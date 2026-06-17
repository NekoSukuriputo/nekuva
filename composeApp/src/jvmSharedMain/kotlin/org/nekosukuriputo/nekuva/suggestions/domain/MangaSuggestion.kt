package org.nekosukuriputo.nekuva.suggestions.domain

import org.nekosukuriputo.nekuva.parsers.model.Manga

/** A suggested manga + its relevance score (0..1) for the suggestions list (port of Doki MangaSuggestion). */
data class MangaSuggestion(
    val manga: Manga,
    val relevance: Float,
)
