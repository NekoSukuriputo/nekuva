package org.nekosukuriputo.nekuva

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.util.Locale
import javax.swing.UIManager
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.di.initKoin

fun main() {
    // Use the OS look & feel for Swing dialogs (the download folder chooser). On Windows this gives the
    // native dialog, which resolves redirected known folders (e.g. OneDrive "Documents") and lets the
    // user create new folders — the cross-platform Metal L&F fails ("Error creating new folder") there.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    initKoin()
    // Kick off the embedded-Chromium (KCEF) download/init in the background so JS-based sources and the
    // in-app browser are ready when needed. First run downloads ~150 MB into ~/.nekuva/kcef.
    runCatching { org.nekosukuriputo.nekuva.core.network.webview.KcefManager.start() }
    // Apply the user's chosen app language BEFORE Compose initialises, so Compose Resources picks the
    // right `values-<lang>` folder. On Desktop the JVM default locale is the OS *regional format* (which
    // can be English even when the display language is not), so an explicit choice is the reliable fix.
    // Empty = follow the JVM/system default. Takes effect on restart.
    runCatching {
        val tag = GlobalContext.get().get<AppSettings>().appLocales
        if (tag.isNotEmpty()) Locale.setDefault(Locale.forLanguageTag(tag))
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nekuva",
        ) {
            App()
        }
    }
}
