package org.nekosukuriputo.nekuva.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.nekosukuriputo.nekuva.core.db.entity.MangaSourceEntity

/** Kotatsu/Doki-compatible "sources" backup row (enable / pin / sort order — drives the Explore order). */
@Serializable
class SourceBackup(
    @SerialName("source") val source: String,
    @SerialName("sort_key") val sortKey: Int,
    @SerialName("used_at") val lastUsedAt: Long = 0L,
    @SerialName("added_in") val addedIn: Int = 0,
    @SerialName("pinned") val isPinned: Boolean = false,
    @SerialName("enabled") val isEnabled: Boolean = true,
) {

    constructor(entity: MangaSourceEntity) : this(
        source = entity.source,
        sortKey = entity.sortKey,
        lastUsedAt = entity.lastUsedAt,
        addedIn = entity.addedIn,
        isPinned = entity.isPinned,
        isEnabled = entity.isEnabled,
    )

    fun toEntity() = MangaSourceEntity(
        source = source,
        isEnabled = isEnabled,
        sortKey = sortKey,
        addedIn = addedIn,
        lastUsedAt = lastUsedAt,
        isPinned = isPinned,
        cfState = 0,
    )
}
