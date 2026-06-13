package org.nekosukuriputo.nekuva.scrobbling.common.domain.model

data class ScrobblingInfo(
    val scrobbler: ScrobblerService,
    val mangaId: Long,
    val targetId: Long,
    val status: ScrobblingStatus?,
    val chapter: Int,
    val comment: String?,
    val rating: Float,
    val title: String,
    val coverUrl: String,
    val description: String?,
    val externalUrl: String,
)
