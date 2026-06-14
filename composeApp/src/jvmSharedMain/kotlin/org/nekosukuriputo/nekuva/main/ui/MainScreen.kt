package org.nekosukuriputo.nekuva.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.*
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.core.nav.*
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.list.ui.ListConfigSheet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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

/** Map a configurable [NavItem] to its tab destination (Doki `nav_main` -> bottom nav). */
fun navItemToTab(item: org.nekosukuriputo.nekuva.core.prefs.NavItem): TabItem = when (item) {
    org.nekosukuriputo.nekuva.core.prefs.NavItem.HISTORY -> TabItem(HistoryTabRoute, Res.string.history, Icons.Filled.History)
    org.nekosukuriputo.nekuva.core.prefs.NavItem.FAVORITES -> TabItem(FavoritesTabRoute, Res.string.favourites, Icons.Filled.Favorite)
    org.nekosukuriputo.nekuva.core.prefs.NavItem.EXPLORE -> TabItem(ExploreRoute, Res.string.explore, Icons.Filled.Explore)
    org.nekosukuriputo.nekuva.core.prefs.NavItem.FEED -> TabItem(FeedTabRoute, Res.string.feed, Icons.Filled.RssFeed)
    org.nekosukuriputo.nekuva.core.prefs.NavItem.LOCAL -> TabItem(HomeRoute, Res.string.local_storage, Icons.Filled.SdStorage)
}

@Composable
fun MainScreen(
    navController: NavHostController,
    isTopLevel: Boolean,
    currentDestination: NavDestination?,
    content: @Composable (PaddingValues) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    // Explicit search session: opened when the field gains focus, closed ONLY by an explicit dismiss
    // (back arrow / submit / picking a suggestion). Tying visibility to raw focus closed the panel the
    // moment any suggestion row/switch was pressed (the press steals focus), eating the click.
    var searchActive by remember { mutableStateOf(false) }
    // "List options" overflow → opens the list-config sheet for the active tab's key (Doki parity).
    val settings = koinInject<AppSettings>()
    var listConfigKey by remember { mutableStateOf<String?>(null) }
    val overflowItems = rememberOverflowItems(navController, currentDestination, onListOptions = { listConfigKey = it })
    // Configurable bottom-nav (Doki nav_main) + label visibility (nav_labels), observed live.
    val navItems by settings.observeNavItems().collectAsState(initial = settings.mainNavItems)
    val tabs = remember(navItems) { navItems.map(::navItemToTab) }
    val navLabelsVisible = settings.isNavLabelsVisible
    // Resume FAB (Doki main_fab): jump back into the last-read manga (resumes via history -> page -1).
    val historyRepo = koinInject<org.nekosukuriputo.nekuva.history.data.HistoryRepository>()
    val lastReadManga by historyRepo.observeLast().collectAsState(initial = null)
    val fabScope = androidx.compose.runtime.rememberCoroutineScope()
    val onResume: () -> Unit = {
        lastReadManga?.let { m ->
            fabScope.launch {
                historyRepo.getOne(m)?.let { h -> navController.navigate(ReaderRoute(m.id, h.chapterId, -1)) }
            }
        }
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // As-you-type search suggestions (Doki parity).
    val suggestionViewModel: org.nekosukuriputo.nekuva.search.ui.suggestion.SearchSuggestionViewModel =
        org.koin.compose.viewmodel.koinViewModel()
    androidx.compose.runtime.LaunchedEffect(searchQuery) {
        suggestionViewModel.onQueryChanged(searchQuery)
    }
    val showSuggestions = isTopLevel && (searchActive || searchQuery.isNotBlank())
    val showResumeFab = isTopLevel && settings.isMainFabEnabled && lastReadManga != null && !showSuggestions

    // Exit confirmation (Doki exit_confirm): at the root (nothing left to pop), a back press shows a
    // "press back again" hint; a second press within 2s quits.
    val snackbarHostState = remember { SnackbarHostState() }
    val confirmExitMsg = stringResource(Res.string.confirm_exit)
    var lastBackMark by remember { mutableStateOf<kotlin.time.TimeSource.Monotonic.ValueTimeMark?>(null) }
    org.nekosukuriputo.nekuva.core.ui.PlatformBackHandler(
        enabled = isTopLevel && settings.isExitConfirmationEnabled && navController.previousBackStackEntry == null,
    ) {
        val mark = lastBackMark
        if (mark != null && mark.elapsedNow() < kotlin.time.Duration.Companion.run { 2.seconds }) {
            org.nekosukuriputo.nekuva.core.ui.exitApp()
        } else {
            lastBackMark = kotlin.time.TimeSource.Monotonic.markNow()
            fabScope.launch { snackbarHostState.showSnackbar(confirmExitMsg) }
        }
    }

    fun dismissSearch() {
        searchQuery = ""
        searchActive = false
        focusManager.clearFocus()
    }

    // A tab/other navigation while the panel is open closes the search session.
    androidx.compose.runtime.LaunchedEffect(currentDestination) {
        if (searchActive) dismissSearch()
    }

    val onSearch: () -> Unit = {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            suggestionViewModel.saveQuery(q)
            dismissSearch()
            navController.navigate(GlobalSearchRoute(q))
        }
    }

    val suggestionPanel: @Composable () -> Unit = {
        org.nekosukuriputo.nekuva.search.ui.suggestion.SearchSuggestionPanel(
            viewModel = suggestionViewModel,
            onQueryClick = { q ->
                suggestionViewModel.saveQuery(q)
                dismissSearch()
                navController.navigate(GlobalSearchRoute(q))
            },
            onTagClick = { tag ->
                dismissSearch()
                navController.navigate(GlobalSearchRoute(tag))
            },
            onMangaClick = { id ->
                dismissSearch()
                navController.navigate(MangaDetailsRoute(id))
            },
            onSourceClick = { sourceId ->
                dismissSearch()
                navController.navigate(RemoteListRoute(sourceId))
            },
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
    }

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
                                alwaysShowLabel = navLabelsVisible,
                                label = if (navLabelsVisible) { { Text(stringResource(tab.titleRes)) } } else null
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (isTopLevel) {
                        MainTopBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = onSearch,
                            overflowItems = overflowItems,
                            searchActive = showSuggestions,
                            onSearchFocusChanged = { if (it) searchActive = true },
                            onCloseSearch = { dismissSearch() },
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues())
                        if (showSuggestions) {
                            suggestionPanel()
                        }
                        if (showResumeFab) {
                            ResumeFab(onResume, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                        }
                    }
                }
            }
        } else {
            Scaffold(
                topBar = {
                    if (isTopLevel) {
                        MainTopBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = onSearch,
                            overflowItems = overflowItems,
                            searchActive = showSuggestions,
                            onSearchFocusChanged = { if (it) searchActive = true },
                            onCloseSearch = { dismissSearch() },
                        )
                    }
                },
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
                                    alwaysShowLabel = navLabelsVisible,
                                    label = if (navLabelsVisible) { { Text(stringResource(tab.titleRes)) } } else null
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = { if (showResumeFab) ResumeFab(onResume) },
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    content(paddingValues)
                    if (showSuggestions) {
                        suggestionPanel()
                    }
                }
            }
        }
    }

    listConfigKey?.let { key ->
        ListConfigSheet(settings = settings, listModeKey = key, onDismiss = { listConfigKey = null })
    }
}

/** Doki's main-screen "resume reading" FAB. */
@Composable
private fun ResumeFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
        text = { Text(stringResource(Res.string.resume)) },
    )
}

/**
 * Doki-style per-tab overflow menu. Only "Settings" is functional; the tab-specific items are shown
 * disabled (their actions are deferred to a polish session — see MIGRATION.md).
 */
@Composable
private fun rememberOverflowItems(
    navController: NavHostController,
    currentDestination: NavDestination?,
    onListOptions: (String) -> Unit,
): List<OverflowItem> {
    val route = currentDestination?.route ?: ""
    // Read all labels unconditionally (stringResource must not be called inside a changing branch).
    val clearHistory = stringResource(Res.string.clear_history)
    val listOptions = stringResource(Res.string.list_options)
    val statistics = stringResource(Res.string.statistics)
    val favCategories = stringResource(Res.string.favourites_categories)
    val filter = stringResource(Res.string.filter)
    val directories = stringResource(Res.string.directories)
    val update = stringResource(Res.string.update)
    val showUpdated = stringResource(Res.string.show_updated)
    val clearFeed = stringResource(Res.string.clear_feed)
    val manageSources = stringResource(Res.string.manage_sources)
    val incognito = stringResource(Res.string.incognito_mode)
    val settingsLabel = stringResource(Res.string.settings)

    fun has(route2: Any) = route.contains(route2::class.qualifiedName ?: " ")
    // "List options" is functional (opens the list-config sheet for that section's key); the other
    // tab items stay disabled until their own polish pass.
    fun disabled(label: String) = OverflowItem(label, enabled = false, onClick = {})
    fun listOpt(key: String) = OverflowItem(listOptions, enabled = true, onClick = { onListOptions(key) })
    val tabItems = when {
        has(HistoryTabRoute) -> listOf(disabled(clearHistory), listOpt(AppSettings.KEY_LIST_MODE_HISTORY), disabled(statistics))
        has(FavoritesTabRoute) -> listOf(listOpt(AppSettings.KEY_LIST_MODE_FAVORITES), disabled(favCategories))
        has(ExploreRoute) -> listOf(disabled(manageSources))
        has(FeedTabRoute) -> listOf(disabled(update), disabled(showUpdated), disabled(clearFeed))
        has(HomeRoute) -> listOf(disabled(filter), listOpt(AppSettings.KEY_LIST_MODE), disabled(directories))
        else -> emptyList()
    }

    return tabItems +
        OverflowItem(incognito, enabled = false, onClick = {}) +
        OverflowItem(settingsLabel, enabled = true, onClick = { navController.navigate(SettingsRoute) })
}
