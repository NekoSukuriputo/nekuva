package org.nekosukuriputo.nekuva.core.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

@Serializable object HistoryTabRoute
@Serializable object FavoritesTabRoute
@Serializable object FeedTabRoute
@Serializable object CategoriesRoute

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    // Determine the current route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Deep link (launcher dynamic shortcut tap -> open manga details), then consume it.
    val deepLinkMangaId by DeepLinkBus.openMangaId.collectAsState()
    androidx.compose.runtime.LaunchedEffect(deepLinkMangaId) {
        deepLinkMangaId?.let { id ->
            navController.navigate(MangaDetailsRoute(id))
            DeepLinkBus.consume()
        }
    }

    // Top-level destinations that show the bottom bar / navigation rail
    val topLevelRoutes = listOf(
        HistoryTabRoute::class.qualifiedName,
        FavoritesTabRoute::class.qualifiedName,
        ExploreRoute::class.qualifiedName,
        FeedTabRoute::class.qualifiedName,
        HomeRoute::class.qualifiedName // Local tab
    )

    val isTopLevel = currentDestination?.route?.let { route ->
        topLevelRoutes.any { topRoute -> route.contains(topRoute ?: "") }
    } ?: true

    // Central reader-open (Doki reader_multitask): separate task/window when enabled, else in-app nav.
    val openReader = org.nekosukuriputo.nekuva.reader.ui.rememberOpenReader(navController)

    org.nekosukuriputo.nekuva.main.ui.MainScreen(
        navController = navController,
        isTopLevel = isTopLevel,
        currentDestination = currentDestination,
        content = { innerPadding ->
            NavHost(
                navController = navController, 
                startDestination = ExploreRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<HistoryTabRoute> {
                    org.nekosukuriputo.nekuva.history.ui.HistoryScreen(
                        onMangaClick = { id ->
                            navController.navigate(MangaDetailsRoute(id))
                        },
                        onResumeClick = { mangaId, chapterId ->
                            openReader(mangaId, chapterId, -1, false)
                        }
                    )
                }
                composable<FavoritesTabRoute> {
                    org.nekosukuriputo.nekuva.favourites.ui.container.FavouritesScreen(
                        onMangaClick = { id ->
                            navController.navigate(MangaDetailsRoute(id))
                        },
                        onManageCategoriesClick = {
                            navController.navigate(CategoriesRoute)
                        }
                    )
                }
                composable<CategoriesRoute> {
                    org.nekosukuriputo.nekuva.favourites.ui.categories.CategoryListScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                composable<FeedTabRoute> {
                    org.nekosukuriputo.nekuva.tracker.ui.feed.FeedScreen(
                        onMangaClick = { id -> navController.navigate(MangaDetailsRoute(id)) },
                    )
                }
                composable<ExploreRoute> {
                    org.nekosukuriputo.nekuva.explore.ui.ExploreScreen(
                        onSourceClick = { sourceId ->
                            navController.navigate(RemoteListRoute(sourceId))
                        },
                        onSearchSubmit = { query ->
                            navController.navigate(GlobalSearchRoute(query))
                        },
                        onBookmarksClick = {
                            navController.navigate(BookmarksRoute)
                        },
                        onDownloadsClick = {
                            navController.navigate(DownloadsRoute)
                        },
                        onSettingsClick = {
                            navController.navigate(SettingsRoute)
                        }
                    )
                }
                composable<BookmarksRoute> {
                    org.nekosukuriputo.nekuva.bookmarks.ui.BookmarksScreen(
                        onMangaClick = { id -> navController.navigate(MangaDetailsRoute(id)) },
                        onOpenReader = { mangaId, chapterId, page ->
                            // From a bookmark -> incognito (Doki parity): don't write history/progress.
                            openReader(mangaId, chapterId, page, true)
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable<DownloadsRoute> {
                    org.nekosukuriputo.nekuva.download.ui.list.DownloadsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable<GlobalSearchRoute> {
                    org.nekosukuriputo.nekuva.search.ui.GlobalSearchScreen(
                        onMangaClick = { id -> navController.navigate(MangaDetailsRoute(id)) },
                        onSourceMore = { sourceId, query ->
                            navController.navigate(RemoteListRoute(sourceId, query))
                        },
                        onOpenBrowser = { url -> navController.navigate(BrowserRoute(url)) },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable<BrowserRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<BrowserRoute>()
                    org.nekosukuriputo.nekuva.browser.ui.BrowserScreen(
                        url = args.url,
                        title = args.title,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<HomeRoute> { // Represents Local tab
                    org.nekosukuriputo.nekuva.local.ui.LocalListScreen(
                        onMangaClick = { id ->
                            navController.navigate(MangaDetailsRoute(id))
                        }
                    )
                }
                composable<MangaDetailsRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<MangaDetailsRoute>()
                    org.nekosukuriputo.nekuva.details.ui.DetailsScreen(
                        onChapterClick = { mangaId, chapterId ->
                            openReader(mangaId, chapterId, -1, false)
                        },
                        onBookmarkClick = { mangaId, chapterId, page ->
                            // From a bookmark -> incognito (Doki parity).
                            openReader(mangaId, chapterId, page, true)
                        },
                        onPageClick = { mangaId, chapterId, page ->
                            // From the Details "Pages" preview -> open the reader at that page (not incognito).
                            openReader(mangaId, chapterId, page, false)
                        },
                        onOpenDownloads = { navController.navigate(DownloadsRoute) },
                        onBackClick = { navController.popBackStack() },
                        onManageCategoriesClick = {
                            navController.navigate(CategoriesRoute)
                        },
                        onRelatedClick = { id -> navController.navigate(MangaDetailsRoute(id)) },
                    )
                }
                composable<ReaderRoute> {
                    org.nekosukuriputo.nekuva.reader.ui.ReaderScreen(
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onOpenSettings = { navController.navigate(ReaderSettingsRoute) },
                    )
                }
                composable<SettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.SettingsRootScreen(
                        onAppearance = { navController.navigate(AppearanceSettingsRoute) },
                        onRemoteSources = { navController.navigate(SourcesSettingsRoute) },
                        onReader = { navController.navigate(ReaderSettingsRoute) },
                        onStorageNetwork = { navController.navigate(StorageNetworkSettingsRoute) },
                        onDownloads = { navController.navigate(DownloadsSettingsRoute) },
                        onTracker = { navController.navigate(TrackerSettingsRoute) },
                        onServices = { navController.navigate(ServicesSettingsRoute) },
                        onBackup = { navController.navigate(BackupSettingsRoute) },
                        onAbout = { navController.navigate(AboutSettingsRoute) },
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<ReaderSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.reader.ReaderSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onTapActions = { navController.navigate(TapGridConfigRoute) },
                    )
                }
                composable<TapGridConfigRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.reader.TapGridConfigScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<ServicesSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.services.ServicesSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onScrobblerLogin = { serviceId -> navController.navigate(OAuthRoute(serviceId)) },
                        onDiscordLogin = { navController.navigate(DiscordLoginRoute) },
                        onSyncClick = { navController.navigate(SyncSettingsRoute) },
                    )
                }
                composable<SyncSettingsRoute> {
                    org.nekosukuriputo.nekuva.sync.ui.SyncSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<TrackerSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.tracker.TrackerSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onTrackCategories = { navController.navigate(CategoriesRoute) },
                    )
                }
                composable<StorageNetworkSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.network.StorageNetworkScreen(
                        onBackClick = { navController.popBackStack() },
                        onProxy = { navController.navigate(ProxySettingsRoute) },
                        onDataRemoval = { navController.navigate(DataCleanupRoute) },
                    )
                }
                composable<ProxySettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.network.ProxySettingsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<DataCleanupRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.network.DataCleanupScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<BackupSettingsRoute> {
                    org.nekosukuriputo.nekuva.backups.ui.BackupSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<AppearanceSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.appearance.AppearanceSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onNavSections = { navController.navigate(NavConfigRoute) },
                        onProtectSetup = { navController.navigate(ProtectSetupRoute) },
                    )
                }
                composable<ProtectSetupRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.protect.ProtectSetupScreen(
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<SourcesSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.sources.SourcesSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onManageSources = { navController.navigate(SourcesManageRoute) },
                        onCatalog = { navController.navigate(SourcesCatalogRoute) },
                    )
                }
                composable<SourcesManageRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.sources.SourcesManageScreen(
                        onBackClick = { navController.popBackStack() },
                        onCatalog = { navController.navigate(SourcesCatalogRoute) },
                        onSourceSettings = { name -> navController.navigate(SourceSettingsRoute(name)) },
                    )
                }
                composable<SourcesCatalogRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.sources.SourcesCatalogScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<SourceSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.sources.SourceSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenBrowser = { url -> navController.navigate(BrowserRoute(url)) },
                    )
                }
                composable<NavConfigRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.appearance.NavConfigScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<DownloadsSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.downloads.DownloadsSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<AboutSettingsRoute> {
                    org.nekosukuriputo.nekuva.settings.ui.about.AboutSettingsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<RemoteListRoute> { entry ->
                    // Retry the source list after a CloudFlare challenge was solved (result set on pop).
                    val cfResolved by entry.savedStateHandle
                        .getStateFlow("cf_resolved", false)
                        .collectAsState()
                    val remoteVm: org.nekosukuriputo.nekuva.remotelist.ui.RemoteListViewModel =
                        org.koin.compose.viewmodel.koinViewModel()
                    androidx.compose.runtime.LaunchedEffect(cfResolved) {
                        if (cfResolved) {
                            remoteVm.retry()
                            entry.savedStateHandle["cf_resolved"] = false
                        }
                    }
                    org.nekosukuriputo.nekuva.remotelist.ui.RemoteListScreen(
                        viewModel = remoteVm,
                        onMangaClick = { id ->
                            navController.navigate(MangaDetailsRoute(id))
                        },
                        onResolveCloudFlare = { url -> navController.navigate(CloudFlareRoute(url)) },
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                composable<OAuthRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<OAuthRoute>()
                    val manager = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.scrobbling.common.domain.ScrobblerManager>()
                    val configVm = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.settings.ui.services.ScrobblerConfigViewModel>()
                    val service = org.nekosukuriputo.nekuva.scrobbling.common.domain.model.ScrobblerService.entries
                        .find { it.id == args.serviceId }
                    val scrobbler = service?.let { manager[it] }
                    if (scrobbler == null) {
                        androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        org.nekosukuriputo.nekuva.browser.ui.OAuthScreen(
                            authUrl = scrobbler.oauthUrl,
                            onCode = { code ->
                                configVm.completeAuth(service, code)
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                }
                composable<StatsRoute> {
                    org.nekosukuriputo.nekuva.stats.ui.StatsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable<DiscordLoginRoute> {
                    val settings = org.koin.compose.koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
                    org.nekosukuriputo.nekuva.scrobbling.discord.ui.DiscordLoginScreen(
                        onToken = { token ->
                            // Capture = enable RPC (Doki: token presence drives enablement).
                            settings.discordToken = token
                            settings.setPref(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_DISCORD_RPC, true)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
                composable<CloudFlareRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<CloudFlareRoute>()
                    org.nekosukuriputo.nekuva.browser.ui.CloudFlareScreen(
                        url = args.url,
                        onResolved = {
                            navController.previousBackStackEntry?.savedStateHandle?.set("cf_resolved", true)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
        }
    )
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}



