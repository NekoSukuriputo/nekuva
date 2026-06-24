package org.nekosukuriputo.nekuva.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
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
    var showClearHistory by remember { mutableStateOf(false) }
    var showImportChoice by remember { mutableStateOf(false) }
    var showLocalFilter by remember { mutableStateOf(false) }
    var showClearFeed by remember { mutableStateOf(false) }
    // Feed/Updates (Doki opt_feed): the tab toolbar lives in the shell, so Update/Show-updated/Clear-feed +
    // the tab badge are driven here. The update check is shared with the Feed screen (single run at a time).
    val trackingRepo = koinInject<org.nekosukuriputo.nekuva.tracker.domain.TrackingRepository>()
    val trackerUpdate = koinInject<org.nekosukuriputo.nekuva.tracker.domain.TrackerUpdateUseCase>()
    val feedUnread by trackingRepo.observeUnreadUpdatesCount().collectAsState(initial = 0)
    val feedHeaderOn by settings.observeBoolean(AppSettings.KEY_FEED_HEADER, true)
        .collectAsState(initial = settings.isFeedHeaderVisible)
    // App-update available (Doki opt_main action_app_update): a search-box icon + overflow entry when a
    // newer release exists. Check GitHub once per app launch (background); the icon appears if newer.
    val appUpdateRepo = koinInject<org.nekosukuriputo.nekuva.core.github.AppUpdateRepository>()
    val appUpdate by appUpdateRepo.observeAvailableUpdate().collectAsState()
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (appUpdateRepo.observeAvailableUpdate().value == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { appUpdateRepo.fetchUpdate(org.nekosukuriputo.nekuva.core.AppInfo.VERSION_NAME) }
            }
        }
    }
    // Extension-update indicator (Point 4): a separate search-box icon when a newer ext bundle is published.
    // Tapping it goes to About, where the "Update extensions" row is dot-marked. Checked once per launch.
    val extManager = koinInject<org.nekosukuriputo.nekuva.core.extensions.ExtensionManager>()
    val extUpdateAvailable by extManager.updateAvailable.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { extManager.checkForUpdate() }
        }
    }
    // Incognito toggle (Doki opt_main checkable item), observed live so the menu checkbox stays in sync.
    val incognitoOn by settings.observeBoolean(AppSettings.KEY_INCOGNITO_MODE, false)
        .collectAsState(initial = settings.isIncognitoModeEnabled)
    val overflowItems = rememberOverflowItems(
        navController, currentDestination,
        onListOptions = { listConfigKey = it },
        onClearHistory = { showClearHistory = true },
        onImport = { showImportChoice = true },
        onLocalFilter = { showLocalFilter = true },
        incognitoOn = incognitoOn,
        onToggleIncognito = { settings.isIncognitoModeEnabled = !incognitoOn },
        feedHeaderOn = feedHeaderOn,
        onFeedUpdate = { trackerUpdate.updateNow() },
        onShowUpdated = { settings.isFeedHeaderVisible = !feedHeaderOn },
        onClearFeed = { showClearFeed = true },
        appUpdateAvailable = appUpdate != null,
        onAppUpdate = { showAppUpdateDialog = true },
    )
    // Local import (Doki opt_local action_import → ImportDialog): pick a .cbz or a folder, copy + parse.
    val importer = koinInject<org.nekosukuriputo.nekuva.local.domain.MangaImportUseCase>()
    val filePicker = org.nekosukuriputo.nekuva.local.ui.rememberMangaFilePicker()
    // Configurable bottom-nav (Doki nav_main) + label visibility (nav_labels), observed live.
    val navItems by settings.observeNavItems().collectAsState(initial = settings.mainNavItems)
    val tabs = remember(navItems) { navItems.map(::navItemToTab) }
    val navLabelsVisible = settings.isNavLabelsVisible
    // Resume FAB (Doki main_fab): jump back into the last-read manga (resumes via history -> page -1).
    val historyRepo = koinInject<org.nekosukuriputo.nekuva.history.data.HistoryRepository>()
    val lastReadManga by historyRepo.observeLast().collectAsState(initial = null)
    val fabScope = androidx.compose.runtime.rememberCoroutineScope()
    // Central reader-open (Doki reader_multitask): separate task/window when enabled, else in-app nav.
    val openReader = org.nekosukuriputo.nekuva.reader.ui.rememberOpenReader(navController)
    val onResume: () -> Unit = {
        lastReadManga?.let { m ->
            fabScope.launch {
                historyRepo.getOne(m)?.let { h -> openReader(m.id, h.chapterId, -1, false) }
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
                navController.navigate(GlobalSearchRoute(tag, org.nekosukuriputo.nekuva.search.domain.SearchKind.TAG.name))
            },
            onAuthorClick = { author ->
                dismissSearch()
                navController.navigate(GlobalSearchRoute(author, org.nekosukuriputo.nekuva.search.domain.SearchKind.AUTHOR.name))
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
                    // Expandable rail (Doki Desktop): a header toggle expands the rail to show tab labels.
                    // Seed from the "show nav labels" preference, but let the toggle fully own the state
                    // afterwards (don't OR with the setting, or an ON preference makes Collapse a no-op).
                    var railExpanded by rememberSaveable { mutableStateOf(navLabelsVisible) }
                    val showLabels = railExpanded
                    NavigationRail(
                        header = {
                            androidx.compose.material3.IconButton(onClick = { railExpanded = !railExpanded }) {
                                Icon(
                                    if (railExpanded) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                    contentDescription = stringResource(if (railExpanded) Res.string.collapse else Res.string.expand),
                                )
                            }
                        },
                    ) {
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
                                icon = { TabIcon(tab, feedUnread) },
                                alwaysShowLabel = showLabels,
                                label = if (showLabels) { { Text(stringResource(tab.titleRes)) } } else null
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
                            appUpdateAvailable = appUpdate != null,
                            onAppUpdateClick = { showAppUpdateDialog = true },
                            extUpdateAvailable = extUpdateAvailable,
                            onExtUpdateClick = { navController.navigate(AboutSettingsRoute) },
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
                            appUpdateAvailable = appUpdate != null,
                            onAppUpdateClick = { showAppUpdateDialog = true },
                            extUpdateAvailable = extUpdateAvailable,
                            onExtUpdateClick = { navController.navigate(AboutSettingsRoute) },
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
                                    icon = { TabIcon(tab, feedUnread) },
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

    // App-update dialog (Doki AppUpdateActivity): shown from the search-box update icon / overflow entry.
    appUpdate?.let { version ->
        if (showAppUpdateDialog) {
            org.nekosukuriputo.nekuva.settings.ui.about.AppUpdateDialog(
                version = version,
                onDismiss = { showAppUpdateDialog = false },
            )
        }
    }

    listConfigKey?.let { key ->
        ListConfigSheet(settings = settings, listModeKey = key, onDismiss = { listConfigKey = null })
    }

    // Clear history (Doki opt_history): Last 2h / Today / Not in favorites / All — acts on the history DB.
    if (showClearHistory) {
        org.nekosukuriputo.nekuva.history.ui.HistoryClearDialog(
            onDismiss = { showClearHistory = false },
            onClear = { option ->
                showClearHistory = false
                fabScope.launch {
                    when (option) {
                        org.nekosukuriputo.nekuva.history.ui.HistoryClearOption.LAST_2_HOURS ->
                            historyRepo.deleteAfter(System.currentTimeMillis() - 2L * 60 * 60 * 1000)
                        org.nekosukuriputo.nekuva.history.ui.HistoryClearOption.TODAY ->
                            historyRepo.deleteAfter(
                                java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
                                    .toInstant().toEpochMilli(),
                            )
                        org.nekosukuriputo.nekuva.history.ui.HistoryClearOption.NOT_FAVORITE ->
                            historyRepo.deleteNotFavorite()
                        org.nekosukuriputo.nekuva.history.ui.HistoryClearOption.ALL -> historyRepo.clear()
                    }
                }
            },
        )
    }

    // Import choice (Doki ImportDialog): a .cbz archive or a folder of images → MangaImportUseCase.
    if (showImportChoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImportChoice = false },
            title = { Text(stringResource(Res.string._import)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.TextButton(onClick = {
                        showImportChoice = false
                        fabScope.launch {
                            runCatching { filePicker.pickCbz { name, input -> importer.import(name, input) } }
                        }
                    }) { Text(stringResource(Res.string.comics_archive)) }
                    androidx.compose.material3.TextButton(onClick = {
                        showImportChoice = false
                        fabScope.launch {
                            runCatching {
                                filePicker.pickDirectory { name, copyInto ->
                                    importer.importDirectory(name) { dest -> copyInto(dest) }
                                }
                            }
                        }
                    }) { Text(stringResource(Res.string.folder_with_images)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showImportChoice = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    // Local filter sheet (Doki local filter): filter the local library by tags.
    if (showLocalFilter) {
        org.nekosukuriputo.nekuva.local.ui.LocalFilterSheet(onDismiss = { showLocalFilter = false })
    }

    // Clear updates feed (Doki opt_feed action_clear_feed): prompt + optional "clear new-chapter counters".
    if (showClearFeed) {
        var clearCounters by remember { mutableStateOf(true) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearFeed = false },
            title = { Text(stringResource(Res.string.clear_updates_feed)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(Res.string.text_clear_updates_feed_prompt))
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { clearCounters = !clearCounters },
                    ) {
                        androidx.compose.material3.Checkbox(checked = clearCounters, onCheckedChange = { clearCounters = it })
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.clear_new_chapters_counters))
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showClearFeed = false
                    val clearAlsoCounters = clearCounters
                    fabScope.launch {
                        runCatching {
                            trackingRepo.clearLogs()
                            if (clearAlsoCounters) trackingRepo.clearCounters()
                        }
                    }
                }) { Text(stringResource(Res.string.clear)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearFeed = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/** Tab icon, badged with the unread-updates count on the Feed tab (Doki feed badge). */
@Composable
private fun TabIcon(tab: TabItem, feedUnread: Int) {
    val isFeed = tab.route === FeedTabRoute
    if (isFeed && feedUnread > 0) {
        androidx.compose.material3.BadgedBox(
            badge = {
                androidx.compose.material3.Badge {
                    Text(if (feedUnread > 99) "99+" else feedUnread.toString())
                }
            },
        ) {
            Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
        }
    } else {
        Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
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
    onClearHistory: () -> Unit,
    onImport: () -> Unit,
    onLocalFilter: () -> Unit,
    incognitoOn: Boolean,
    onToggleIncognito: () -> Unit,
    feedHeaderOn: Boolean,
    onFeedUpdate: () -> Unit,
    onShowUpdated: () -> Unit,
    onClearFeed: () -> Unit,
    appUpdateAvailable: Boolean,
    onAppUpdate: () -> Unit,
): List<OverflowItem> {
    val route = currentDestination?.route ?: ""
    // Read all labels unconditionally (stringResource must not be called inside a changing branch).
    val clearHistory = stringResource(Res.string.clear_history)
    val importLabel = stringResource(Res.string._import)
    val directoriesLabel = stringResource(Res.string.directories)
    val listOptions = stringResource(Res.string.list_options)
    val statistics = stringResource(Res.string.statistics)
    val favCategories = stringResource(Res.string.favourites_categories)
    val filter = stringResource(Res.string.filter)
    val directories = stringResource(Res.string.directories)
    val update = stringResource(Res.string.update)
    val showUpdated = stringResource(Res.string.show_updated)
    val clearFeed = stringResource(Res.string.clear_feed)
    val manageSources = stringResource(Res.string.manage_sources)
    val sourcesCatalog = stringResource(Res.string.sources_catalog)
    val incognito = stringResource(Res.string.incognito_mode)
    val settingsLabel = stringResource(Res.string.settings)
    val appUpdateLabel = stringResource(Res.string.app_update_available)

    fun has(route2: Any) = route.contains(route2::class.qualifiedName ?: " ")
    // "List options" is functional (opens the list-config sheet for that section's key); the other
    // tab items stay disabled until their own polish pass.
    fun disabled(label: String) = OverflowItem(label, enabled = false, onClick = {})
    fun listOpt(key: String) = OverflowItem(listOptions, enabled = true, onClick = { onListOptions(key) })
    val tabItems = when {
        has(HistoryTabRoute) -> listOf(OverflowItem(clearHistory, enabled = true, onClick = onClearHistory), listOpt(AppSettings.KEY_LIST_MODE_HISTORY), OverflowItem(statistics, enabled = true, onClick = { navController.navigate(StatsRoute) }))
        has(FavoritesTabRoute) -> listOf(
            listOpt(AppSettings.KEY_LIST_MODE_FAVORITES),
            OverflowItem(favCategories, enabled = true, onClick = { navController.navigate(CategoriesRoute) }),
        )
        has(ExploreRoute) -> listOf(
            OverflowItem(manageSources, enabled = true, onClick = { navController.navigate(SourcesSettingsRoute) }),
            OverflowItem(sourcesCatalog, enabled = true, onClick = { navController.navigate(SourcesCatalogRoute) }),
        )
        // Feed/Updates (Doki opt_feed): Update (manual check), Show updated (checkable header toggle), Clear feed.
        has(FeedTabRoute) -> listOf(
            OverflowItem(update, enabled = true, onClick = onFeedUpdate),
            OverflowItem(showUpdated, enabled = true, onClick = onShowUpdated, checked = feedHeaderOn),
            OverflowItem(clearFeed, enabled = true, onClick = onClearFeed),
        )
        // Local (Doki opt_local): Filter + Import + List options + Directories.
        has(HomeRoute) -> listOf(
            OverflowItem(filter, enabled = true, onClick = onLocalFilter),
            OverflowItem(importLabel, enabled = true, onClick = onImport),
            listOpt(AppSettings.KEY_LIST_MODE),
            OverflowItem(directoriesLabel, enabled = true, onClick = { navController.navigate(org.nekosukuriputo.nekuva.core.nav.MangaDirectoriesRoute) }),
        )
        else -> emptyList()
    }

    // Doki opt_main order: App-update (prominent, only when available) → tab items → Incognito → Settings.
    val appUpdateItem = if (appUpdateAvailable) listOf(OverflowItem(appUpdateLabel, enabled = true, onClick = onAppUpdate)) else emptyList()
    return appUpdateItem + tabItems +
        OverflowItem(incognito, enabled = true, checked = incognitoOn, onClick = onToggleIncognito) +
        OverflowItem(settingsLabel, enabled = true, onClick = { navController.navigate(SettingsRoute) })
}
