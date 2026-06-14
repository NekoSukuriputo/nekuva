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

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        val repositoryFactory = org.koin.core.context.GlobalContext.get().get<org.nekosukuriputo.nekuva.core.parser.MangaRepository.Factory>()
        ImageLoader.Builder(context).components {
            // Local manga page/cover images (zip:/file:) must come before the network fetcher.
            add(org.nekosukuriputo.nekuva.core.image.LocalImageFetcher.Factory())
            add(KtorNetworkFetcherFactory(createHttpClient()))
            add(org.nekosukuriputo.nekuva.core.image.FaviconFetcher.Factory(repositoryFactory))
        }.crossfade(true).build()
    }
    
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
            org.nekosukuriputo.nekuva.core.nav.AppNavigation()
        }
      }
    }
}

