package org.nekosukuriputo.nekuva.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FavouriteCategory(
    val id: Long,
    val title: String,
    val sortKey: Int,
    val createdAt: Long,
    val isTrackingEnabled: Boolean,
    val isVisibleInLibrary: Boolean,
)
