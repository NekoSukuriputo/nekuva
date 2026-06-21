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
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
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
        // Use the app OkHttp client for image network calls: it carries the DoH resolver, CloudFlare
        // clearance cookies (shared with the WebView) and rate-limiting, so covers/pages/thumbnails from
        // CloudFlare/DoH-protected sources load just like the parser's requests do (Doki parity).
        // Add CommonHeadersInterceptor so the X-Manga-Source header (set per request from the source extra)
        // resolves to the source's Referer/User-Agent + per-source interceptor (CloudFlare).
        val koin = org.koin.core.context.GlobalContext.get()
        val okHttpClient = koin.get<okhttp3.OkHttpClient>().newBuilder()
            .apply {
                // Prepend (index 0 = outermost, Doki order) so the per-source Referer/User-Agent are set
                // BEFORE the CloudFlare interceptor inspects the request — matching the parser's own chain.
                interceptors().add(
                    0,
                    org.nekosukuriputo.nekuva.core.network.CommonHeadersInterceptor(
                        lazy { koin.get<org.nekosukuriputo.nekuva.core.parser.MangaRepository.Factory>() },
                        lazy { koin.get<org.nekosukuriputo.nekuva.parsers.MangaLoaderContext>() },
                    ),
                )
            }
            .build()
        ImageLoader.Builder(context).components {
            // Per-source HTTP headers (Referer, UA) on image requests (Doki MangaSourceHeaderInterceptor).
            add(org.nekosukuriputo.nekuva.core.image.MangaSourceHeaderInterceptor())
            // Local manga page/cover images (zip:/file:) must come before the network fetcher.
            add(org.nekosukuriputo.nekuva.core.image.LocalImageFetcher.Factory())
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
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

    // Background suggestions refresh (Doki SuggestionsWorker): (re)schedule per the suggestions setting on
    // launch. Android = WorkManager periodic; Desktop = no-op (suggestions stay on-demand from the screen).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { org.nekosukuriputo.nekuva.suggestions.work.scheduleSuggestions() }
    }

    // Background batch auto-fix (Doki AutoFixService): (re)schedule per the auto_fix_broken setting on launch.
    // Android = WorkManager periodic; Desktop = no-op.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { org.nekosukuriputo.nekuva.alternatives.work.scheduleAutoFix() }
    }

    // Periodic backup (Doki PeriodicalBackupWorker): (re)schedule per the backup setting on launch.
    // Android = WorkManager; Desktop = no-op.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { org.nekosukuriputo.nekuva.backups.work.scheduleBackup() }
    }

    // App-update check on launch (Doki AppUpdateRepository): populates the "App update available" menu item
    // in the main shell when a newer GitHub release exists. Failures are swallowed (offline / rate-limited).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            org.koin.core.context.GlobalContext.get()
                .get<org.nekosukuriputo.nekuva.core.github.AppUpdateRepository>()
                .fetchUpdate(org.nekosukuriputo.nekuva.core.AppInfo.VERSION_NAME)
        }
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

