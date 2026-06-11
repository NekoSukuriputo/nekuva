package org.nekosukuriputo.nekuva

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import javax.swing.UIManager
import org.nekosukuriputo.nekuva.di.initKoin

fun main() {
    // Use the OS look & feel for Swing dialogs (the download folder chooser). On Windows this gives the
    // native dialog, which resolves redirected known folders (e.g. OneDrive "Documents") and lets the
    // user create new folders — the cross-platform Metal L&F fails ("Error creating new folder") there.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nekuva",
        ) {
            App()
        }
    }
}
