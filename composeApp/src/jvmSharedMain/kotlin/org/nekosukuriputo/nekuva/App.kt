package org.nekosukuriputo.nekuva

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import org.nekosukuriputo.nekuva.network.createHttpClient
import org.nekosukuriputo.nekuva.core.parser.AppMangaLoaderContext
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.read_later
import org.nekosukuriputo.nekuva.favourites.domain.FavouritesRepository

/**
 * Installs Nekuva's singleton Coil [ImageLoader] (local/zip + network + favicon fetchers, on-disk cache).
 * Shared by [App] and the standalone reader window/Activity (Doki reader_multitask) so a reader opened in
 * its own task still loads pages/favicons with the same pipeline.
 */
@Composable
fun InstallNekuvaImageLoader() {
    setSingletonImageLoaderFactory { context ->
        val faviconCache = org.koin.core.context.GlobalContext.get().get<org.nekosukuriputo.nekuva.core.image.FaviconCache>()
        ImageLoader.Builder(context).components {
            // Local manga page/cover images (zip:/file:) must come before the network fetcher.
            add(org.nekosukuriputo.nekuva.core.image.LocalImageFetcher.Factory())
            add(KtorNetworkFetcherFactory(createHttpClient()))
            add(org.nekosukuriputo.nekuva.core.image.FaviconFetcher.Factory(faviconCache))
        }
            // Persist images (esp. source favicons) on disk so they're fetched once, not every launch (Doki).
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(org.nekosukuriputo.nekuva.core.image.imageDiskCacheDir(context))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true).build()
    }
}

@Composable
fun App() {
    InstallNekuvaImageLoader()

    val parserContext = koinInject<AppMangaLoaderContext>()
    var dummyData by remember { mutableStateOf("Loading...") }
    
    LaunchedEffect(Unit) {
        dummyData = parserContext.fetchDummyData()
    }

    // Localise the seeded "Read Later" category to the device language (new + existing DBs).
    val readLaterTitle = stringResource(Res.string.read_later)
    LaunchedEffect(readLaterTitle) {
        org.koin.core.context.GlobalContext.get()
            .get<FavouritesRepository>()
            .localizeSeededReadLater(readLaterTitle)
    }

    // Live theme: react to the appearance settings (system / light / dark + AMOLED).
    val settings = koinInject<org.nekosukuriputo.nekuva.core.prefs.AppSettings>()
    val themeMode by settings.observeTheme().collectAsState(initial = settings.theme)
    val amoled by settings.observeAmoled().collectAsState(initial = settings.isAmoledTheme)
    val colorSchemeName by settings.observeColorScheme().collectAsState(initial = settings.colorScheme.name)
    val darkTheme = when (themeMode) {
        1 -> false // light
        2 -> true // dark
        else -> androidx.compose.foundation.isSystemInDarkTheme() // -1 = follow system
    }

    // Launcher dynamic shortcuts (Doki dynamic_shortcuts): keep them in sync with recent history
    // (Android only; Desktop is a no-op). Clears them when the setting is off.
    val historyRepoForShortcuts = koinInject<org.nekosukuriputo.nekuva.history.data.HistoryRepository>()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        historyRepoForShortcuts.observeAll(4).collect { recent ->
            val shortcuts = if (settings.isDynamicShortcutsEnabled) {
                recent.map { org.nekosukuriputo.nekuva.core.shortcuts.MangaShortcut(it.id, it.title) }
            } else {
                emptyList()
            }
            org.nekosukuriputo.nekuva.core.shortcuts.updateDynamicShortcuts(shortcuts)
        }
    }

    // Ad blocker (Doki AdListUpdateService): download/refresh EasyList on launch when adblock is on, so
    // the in-app browser's request interception has rules to match.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (settings.isAdBlockEnabled) {
            runCatching {
                org.koin.core.context.GlobalContext.get()
                    .get<org.nekosukuriputo.nekuva.core.network.webview.adblock.AdBlock>()
                    .updateList()
            }
        }
    }

    // Auto-delete read chapters on start (Doki chapters_clear_auto) to free space.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (settings.prefBoolean(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_CHAPTERS_CLEAR_AUTO, false)) {
            runCatching {
                org.koin.core.context.GlobalContext.get()
                    .get<org.nekosukuriputo.nekuva.local.domain.DeleteReadChaptersUseCase>()
                    .invoke()
            }
        }
    }

    // Background tracker (Doki TrackWorker): (re)schedule the periodic new-chapter check on launch per the
    // current tracker settings. Android = WorkManager; Desktop = no-op (tracker stays manual via the Feed).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { org.nekosukuriputo.nekuva.tracker.work.scheduleTracker() }
    }

    // Screenshots policy (Doki screenshots_policy): block at the window for BLOCK_ALL, or for
    // BLOCK_INCOGNITO while global incognito is on. NSFW/per-reader cases are handled in those screens.
    val screenshotsPolicy by settings.observeScreenshotsPolicy()
        .collectAsState(initial = settings.screenshotsPolicy)
    val globalIncognito by settings.observeBoolean(org.nekosukuriputo.nekuva.core.prefs.AppSettings.KEY_INCOGNITO_MODE, false)
        .collectAsState(initial = settings.isIncognitoModeEnabled)
    org.nekosukuriputo.nekuva.core.ui.SecureScreenEffect(
        secure = screenshotsPolicy == org.nekosukuriputo.nekuva.core.prefs.ScreenshotsPolicy.BLOCK_ALL ||
            (screenshotsPolicy == org.nekosukuriputo.nekuva.core.prefs.ScreenshotsPolicy.BLOCK_INCOGNITO && globalIncognito),
    )

    // In-app language (Doki app_locale). Re-key on the locale so Compose Resources re-resolve; the
    // remember applies the JVM/Desktop default before children compose (Android uses the Activity config).
    val localeTag by settings.observeAppLocale().collectAsState(initial = settings.appLocales)
    androidx.compose.runtime.key(localeTag) {
      remember(localeTag) { org.nekosukuriputo.nekuva.core.i18n.applyAppLocale(localeTag) }
      org.nekosukuriputo.nekuva.core.ui.theme.NekuvaTheme(darkTheme = darkTheme, amoled = amoled, colorSchemeName = colorSchemeName) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                org.nekosukuriputo.nekuva.core.nav.AppNavigation()
                // App-lock gate (Doki protect_app): cover everything until unlocked.
                remember { org.nekosukuriputo.nekuva.core.security.AppLockController.initIfNeeded(settings) }
                if (org.nekosukuriputo.nekuva.core.security.AppLockController.isLocked) {
                    org.nekosukuriputo.nekuva.settings.ui.protect.LockScreen()
                }
            }
        }
      }
    }
}

