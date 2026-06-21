package org.nekosukuriputo.nekuva.search.domain

/**
 * What a search query means (Doki SearchKind). Drives how each source/DB section builds its filter:
 * [SIMPLE]/[TITLE] = free text, [AUTHOR] = author filter, [TAG] = resolve the tag and filter by it.
 */
enum class SearchKind {
    SIMPLE, TITLE, AUTHOR, TAG
}
