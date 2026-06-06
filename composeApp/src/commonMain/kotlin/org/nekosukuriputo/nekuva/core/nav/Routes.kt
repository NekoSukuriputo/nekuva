package org.nekosukuriputo.nekuva.core.nav

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class MangaDetailsRoute(val mangaId: Long)

@Serializable
data class ReaderRoute(val mangaId: Long, val chapterId: Long)

@Serializable
data object SettingsRoute

@Serializable
data object ExploreRoute

@Serializable
data class RemoteListRoute(val sourceId: String)
