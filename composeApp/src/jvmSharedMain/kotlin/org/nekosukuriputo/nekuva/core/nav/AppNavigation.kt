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

@Serializable object HistoryTabRoute
@Serializable object FavoritesTabRoute
@Serializable object FeedTabRoute
@Serializable object CategoriesRoute

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    // Determine the current route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
                            navController.navigate(ReaderRoute(mangaId, chapterId))
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
                    org.nekosukuriputo.nekuva.main.ui.EmptyTabScreen("Feed / Updates (Not implemented yet)")
                }
                composable<ExploreRoute> {
                    org.nekosukuriputo.nekuva.explore.ui.ExploreScreen(
                        onSourceClick = { sourceId ->
                            navController.navigate(RemoteListRoute(sourceId))
                        }
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
                            navController.navigate(ReaderRoute(mangaId, chapterId))
                        },
                        onBackClick = { navController.popBackStack() },
                        onManageCategoriesClick = {
                            navController.navigate(CategoriesRoute)
                        }
                    )
                }
                composable<ReaderRoute> {
                    org.nekosukuriputo.nekuva.reader.ui.ReaderScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                composable<SettingsRoute> {
                    PlaceholderScreen("Settings")
                }
                composable<RemoteListRoute> {
                    org.nekosukuriputo.nekuva.remotelist.ui.RemoteListScreen(
                        onMangaClick = { id ->
                            navController.navigate(MangaDetailsRoute(id))
                        },
                        onBackClick = {
                            navController.popBackStack()
                        }
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



