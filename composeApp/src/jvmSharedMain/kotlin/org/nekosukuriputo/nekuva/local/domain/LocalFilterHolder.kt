package org.nekosukuriputo.nekuva.local.domain

import kotlinx.coroutines.flow.MutableStateFlow
import org.nekosukuriputo.nekuva.parsers.model.ContentRating
import org.nekosukuriputo.nekuva.parsers.model.MangaTag

/**
 * Shared local-library filter state (Doki local filter sheet). The filter sheet (opened from the main
 * toolbar's "Filter") writes the selection here; [org.nekosukuriputo.nekuva.local.ui.LocalListViewModel]
 * observes it and re-queries. A singleton bridges the shell toolbar and the local list (separate VMs).
 * Mirrors Doki's local filter capabilities: include genres, exclude genres, content rating.
 */
class LocalFilterHolder {
    val tags = MutableStateFlow<Set<MangaTag>>(emptySet())
    val tagsExclude = MutableStateFlow<Set<MangaTag>>(emptySet())
    val contentRating = MutableStateFlow<Set<ContentRating>>(emptySet())

    /** Bumped once when the filter sheet applies — the local list observes this to re-query exactly once
     *  (the sort change writes AppSettings, which isn't a flow, so this is the single reload trigger). */
    val revision = MutableStateFlow(0)

    val isEmpty: Boolean
        get() = tags.value.isEmpty() && tagsExclude.value.isEmpty() && contentRating.value.isEmpty()

    fun reset() {
        tags.value = emptySet()
        tagsExclude.value = emptySet()
        contentRating.value = emptySet()
    }

    fun notifyApplied() {
        revision.value += 1
    }
}
