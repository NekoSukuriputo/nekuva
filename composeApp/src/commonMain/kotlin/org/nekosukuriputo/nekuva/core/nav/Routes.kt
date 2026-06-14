package org.nekosukuriputo.nekuva.core.nav

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class MangaDetailsRoute(val mangaId: Long)

// [incognito] = opened from a bookmark (Doki forces incognito so it doesn't touch history/progress).
@Serializable
data class ReaderRoute(val mangaId: Long, val chapterId: Long, val page: Int, val incognito: Boolean = false)

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
data class OAuthRoute(val serviceId: Int)

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

@Serializable
data object SyncSettingsRoute

@Serializable
data object TapGridConfigRoute

@Serializable
data object NavConfigRoute
