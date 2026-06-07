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
import androidx.navigation.toRoute

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ExploreRoute) {
        composable<HomeRoute> {
            org.nekosukuriputo.nekuva.local.ui.LocalListScreen(
                onMangaClick = { id ->
                    navController.navigate(MangaDetailsRoute(id))
                }
            )
        }
        composable<MangaDetailsRoute> {
            org.nekosukuriputo.nekuva.details.ui.DetailsScreen(
                onChapterClick = { mangaId, chapterId ->
                    navController.navigate(ReaderRoute(mangaId, chapterId))
                },
                onBackClick = {
                    navController.popBackStack()
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
        composable<ExploreRoute> {
            org.nekosukuriputo.nekuva.explore.ui.ExploreScreen(
                onSourceClick = { sourceId ->
                    navController.navigate(RemoteListRoute(sourceId))
                }
            )
        }
        composable<RemoteListRoute> {
            org.nekosukuriputo.nekuva.remotelist.ui.RemoteListScreen(
                onMangaClick = { id ->
                    navController.navigate(MangaDetailsRoute(id))
                }
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}



