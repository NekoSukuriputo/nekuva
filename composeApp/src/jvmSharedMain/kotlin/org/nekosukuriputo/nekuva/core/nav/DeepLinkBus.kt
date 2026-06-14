package org.nekosukuriputo.nekuva.core.nav

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot deep-link target (e.g. a launcher dynamic shortcut tap → open a manga). Set by the platform
 * entry point (Android MainActivity intent), observed by the nav host, then consumed.
 */
object DeepLinkBus {
    val openMangaId = MutableStateFlow<Long?>(null)
    fun requestOpenManga(id: Long) { openMangaId.value = id }
    fun consume() { openMangaId.value = null }
}
