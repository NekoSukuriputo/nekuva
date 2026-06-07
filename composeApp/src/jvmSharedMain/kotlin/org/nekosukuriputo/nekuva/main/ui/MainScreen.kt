package org.nekosukuriputo.nekuva.main.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*
import org.nekosukuriputo.nekuva.core.nav.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.ui.graphics.vector.ImageVector

data class TabItem(
    val route: Any,
    val titleRes: org.jetbrains.compose.resources.StringResource,
    val icon: ImageVector
)

val tabs = listOf(
    TabItem(HistoryTabRoute, Res.string.history, Icons.Filled.History),
    TabItem(FavoritesTabRoute, Res.string.favourites, Icons.Filled.Favorite),
    TabItem(ExploreRoute, Res.string.explore, Icons.Filled.Explore),
    TabItem(FeedTabRoute, Res.string.feed, Icons.Filled.RssFeed),
    TabItem(HomeRoute, Res.string.local_storage, Icons.Filled.SdStorage)
)

@Composable
fun MainScreen(
    navController: NavHostController,
    isTopLevel: Boolean,
    currentDestination: NavDestination?,
    content: @Composable (PaddingValues) -> Unit
) {
    BoxWithConstraints {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isTopLevel) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { 
                                it.route?.contains(tab.route::class.qualifiedName ?: "") == true
                            } == true

                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.titleRes)) },
                                label = { Text(stringResource(tab.titleRes)) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    content(PaddingValues())
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (isTopLevel) {
                        NavigationBar {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { 
                                    it.route?.contains(tab.route::class.qualifiedName ?: "") == true
                                } == true

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.titleRes)) },
                                    label = { Text(stringResource(tab.titleRes)) }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    content(paddingValues)
                }
            }
        }
    }
}
