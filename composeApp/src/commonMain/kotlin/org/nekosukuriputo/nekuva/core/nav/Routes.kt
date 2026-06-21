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
data class RemoteListRoute(
    val sourceId: String,
    val query: String? = null,
    // Pre-applied genre filter (Doki openList(tag)) — carried as primitives so the route stays in commonMain.
    val tagKey: String? = null,
    val tagTitle: String? = null,
    // Pre-applied author filter (Doki openList(source, MangaListFilter(author=…))).
    val author: String? = null,
)

@Serializable
data object HistoryRoute

@Serializable
data class GlobalSearchRoute(
    val query: String,
    // Doki SearchKind (SIMPLE/AUTHOR/TAG) stored as its name — kept a primitive so the nav arg needs no
    // custom enum NavType. Parsed back via SearchKind.valueOf in the ViewModel.
    val kind: String = org.nekosukuriputo.nekuva.search.domain.SearchKind.SIMPLE.name,
)

// "Find similar in other sources" (Doki AlternativesActivity): same manga title searched across sources.
@Serializable
data class AlternativesRoute(val mangaId: Long)

/** Doki "Find similar" (action_related): related-manga list for a seed manga. */
@Serializable
data class RelatedRoute(val mangaId: Long)

@Serializable
data class BrowserRoute(val url: String, val title: String? = null)

@Serializable
data class CloudFlareRoute(val url: String)

@Serializable
data class OAuthRoute(val serviceId: Int)

@Serializable
data object DiscordLoginRoute

@Serializable
data object StatsRoute

@Serializable
data object SuggestionsRoute

@Serializable
data object PeriodicBackupRoute

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
data object ProxySettingsRoute

@Serializable
data object DataCleanupRoute

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
data object ProtectSetupRoute

@Serializable
data object SourcesSettingsRoute

@Serializable
data object SourcesManageRoute

@Serializable
data object SourcesCatalogRoute

@Serializable
data class SourceSettingsRoute(val sourceName: String)

@Serializable
data object NavConfigRoute
