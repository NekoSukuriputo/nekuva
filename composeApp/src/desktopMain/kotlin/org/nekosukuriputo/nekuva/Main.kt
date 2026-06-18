package org.nekosukuriputo.nekuva

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.util.Locale
import javax.swing.UIManager
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.di.initKoin

fun main() {
    // Install Conscrypt as the top JSSE provider so Desktop uses the same modern TLS stack as Android.
    // The stock Linux JVM's default TLS can lack ciphers/ALPN that some manga CDNs require, making a
    // source fail on Linux Desktop while working on Windows + Android. Best-effort: if Conscrypt can't
    // load, fall back to the platform default rather than crashing startup.
    runCatching {
        java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
    }
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
        // App/window icon from the generated PNG on the classpath (desktopMain/resources/nekuva_icon.png).
        val appIcon = painterResource("nekuva_icon.png")
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nekuva",
            icon = appIcon,
        ) {
            App()
        }
        // Standalone reader windows (Doki reader_multitask): one per open entry, closeable independently.
        for (entry in org.nekosukuriputo.nekuva.reader.ui.DesktopReaderWindows.windows) {
            androidx.compose.runtime.key(entry.id) {
                Window(
                    onCloseRequest = { org.nekosukuriputo.nekuva.reader.ui.DesktopReaderWindows.close(entry) },
                    title = "Nekuva — Reader",
                    icon = appIcon,
                ) {
                    org.nekosukuriputo.nekuva.reader.ui.ReaderWindowHost(
                        args = entry.args,
                        onClose = { org.nekosukuriputo.nekuva.reader.ui.DesktopReaderWindows.close(entry) },
                    )
                }
            }
        }
    }
}
