package org.nekosukuriputo.nekuva.scrobbling.common.domain.model

data class ScrobblerManga(
    val id: Long,
    val name: String,
    val altName: String?,
    val cover: String?,
    val url: String,
    val isBestMatch: Boolean,
)
