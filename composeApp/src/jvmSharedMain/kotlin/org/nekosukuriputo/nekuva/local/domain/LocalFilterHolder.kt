package org.nekosukuriputo.nekuva.local.domain

import kotlinx.coroutines.flow.MutableStateFlow
import org.nekosukuriputo.nekuva.parsers.model.MangaTag

/**
 * Shared local-library filter state (Doki local filter). The filter sheet (opened from the main toolbar's
 * "Filter") writes the selected tags here; [org.nekosukuriputo.nekuva.local.ui.LocalListViewModel] observes
 * it and re-queries. A singleton bridges the shell toolbar and the local list (which live in separate VMs).
 */
class LocalFilterHolder {
    val tags = MutableStateFlow<Set<MangaTag>>(emptySet())
}
