package org.nekosukuriputo.nekuva.core.nav

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class MangaDetailsRoute(val mangaId: Long)

@Serializable
data class ReaderRoute(val mangaId: Long, val chapterId: Long, val page: Int)

@Serializable
data object SettingsRoute

@Serializable
data object ExploreRoute

@Serializable
data class RemoteListRoute(val sourceId: String, val query: String? = null)

@Serializable
data object HistoryRoute

@Serializable
data class GlobalSearchRoute(val query: String)

@Serializable
data class BrowserRoute(val url: String, val title: String? = null)

@Serializable
data class CloudFlareRoute(val url: String)

@Serializable
data object BookmarksRoute

@Serializable
data object DownloadsRoute

@Serializable
data object AppearanceSettingsRoute

@Serializable
data object DownloadsSettingsRoute

@Serializable
data object AboutSettingsRoute

@Serializable
data object BackupSettingsRoute

@Serializable
data object StorageNetworkSettingsRoute

@Serializable
data object ReaderSettingsRoute

@Serializable
data object ServicesSettingsRoute

@Serializable
data object TrackerSettingsRoute
