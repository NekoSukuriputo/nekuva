package org.nekosukuriputo.nekuva.settings.ui.network

/** Storage breakdown for the usage meter (Doki StorageUsage): saved manga / pages / other / available. */
data class StorageUsage(
    val savedManga: Item,
    val pagesCache: Item,
    val otherCache: Item,
    val available: Item,
) {
    data class Item(val bytes: Long, val percent: Float)
}
