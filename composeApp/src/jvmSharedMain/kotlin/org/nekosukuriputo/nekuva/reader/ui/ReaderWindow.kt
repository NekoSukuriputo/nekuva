package org.nekosukuriputo.nekuva.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import org.nekosukuriputo.nekuva.InstallNekuvaImageLoader
import org.nekosukuriputo.nekuva.core.nav.ReaderRoute
import org.nekosukuriputo.nekuva.core.nav.ReaderSettingsRoute
import org.nekosukuriputo.nekuva.core.nav.TapGridConfigRoute
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.core.ui.theme.NekuvaTheme
import org.nekosukuriputo.nekuva.settings.ui.reader.ReaderSettingsScreen
import org.nekosukuriputo.nekuva.settings.ui.reader.TapGridConfigScreen

/** Parameters needed to open the reader in a standalone task/window (Doki reader_multitask). */
data class ReaderArgs(val mangaId: Long, val chapterId: Long, val page: Int, val incognito: Boolean)

/**
 * Opens the reader in a SEPARATE task (Android, `FLAG_ACTIVITY_NEW_DOCUMENT`) or window (Desktop) — Doki's
 * `reader_multitask`. Returns false when the platform can't (caller then falls back to in-app navigation).
 */
fun interface ReaderWindowLauncher {
    fun open(args: ReaderArgs): Boolean
}

@Composable
expect fun rememberReaderWindowLauncher(): ReaderWindowLauncher

/**
 * Central reader-open used by every launch site. When `reader_multitask` is on it opens the reader in its
 * own task/window; otherwise (and if the platform launcher declines) it navigates in-app as before.
 */
@Composable
fun rememberOpenReader(navController: NavController): (mangaId: Long, chapterId: Long, page: Int, incognito: Boolean) -> Unit {
    val launcher = rememberReaderWindowLauncher()
    val settings = koinInject<AppSettings>()
    val multitask = remember { settings.isReaderMultiTaskEnabled }
    return remember(launcher, multitask, navController) {
        { mangaId, chapterId, page, incognito ->
            val handled = multitask && launcher.open(ReaderArgs(mangaId, chapterId, page, incognito))
            if (!handled) navController.navigate(ReaderRoute(mangaId, chapterId, page, incognito))
        }
    }
}

/**
 * Self-contained reader host for a standalone task/window: applies the Nekuva image loader + theme and runs
 * its OWN minimal NavHost (Reader + reader settings) so [ReaderViewModel]'s `SavedStateHandle.toRoute` keeps
 * working exactly as in the main app. [onClose] finishes the Activity (Android) / closes the window (Desktop).
 */
@Composable
fun ReaderWindowHost(args: ReaderArgs, onClose: () -> Unit) {
    InstallNekuvaImageLoader()
    val settings = koinInject<AppSettings>()
    val themeMode by settings.observeTheme().collectAsState(initial = settings.theme)
    val amoled by settings.observeAmoled().collectAsState(initial = settings.isAmoledTheme)
    val colorSchemeName by settings.observeColorScheme().collectAsState(initial = settings.colorScheme.name)
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    NekuvaTheme(darkTheme = darkTheme, amoled = amoled, colorSchemeName = colorSchemeName) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = ReaderRoute(args.mangaId, args.chapterId, args.page, args.incognito),
            ) {
                composable<ReaderRoute> {
                    ReaderScreen(
                        onBackClick = onClose,
                        onOpenSettings = { nav.navigate(ReaderSettingsRoute) },
                    )
                }
                composable<ReaderSettingsRoute> {
                    ReaderSettingsScreen(
                        onBackClick = { nav.popBackStack() },
                        onTapActions = { nav.navigate(TapGridConfigRoute) },
                    )
                }
                composable<TapGridConfigRoute> {
                    TapGridConfigScreen(onBackClick = { nav.popBackStack() })
                }
            }
        }
    }
}
