package org.nekosukuriputo.nekuva.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MangaHistory(
    val createdAt: Long,
    val updatedAt: Long,
    val chapterId: Long,
    val page: Int,
    val scroll: Int,
    val percent: Float,
    val chaptersCount: Int,
)
