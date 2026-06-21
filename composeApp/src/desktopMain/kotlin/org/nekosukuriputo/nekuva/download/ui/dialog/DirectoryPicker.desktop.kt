package org.nekosukuriputo.nekuva.download.ui.dialog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual val supportsDirectoryPicker: Boolean = true

// The native Windows look & feel is set once at app startup (Main.kt), so the chooser here resolves
// redirected known folders (OneDrive "Documents") and supports creating new folders.
actual suspend fun pickMangaDirectory(): String? = withContext(Dispatchers.IO) {
    var result: String? = null
    SwingUtilities.invokeAndWait {
        // Start in the user's home, NOT the JVM working dir (= the project dir when run via Gradle),
        // so the project folder isn't accidentally added as a manga directory.
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            // Disable Windows ShellFolder integration — it does blocking shell calls on the EDT and
            // freezes the chooser while navigating folders. (FileDialog can't pick directories on Windows.)
            putClientProperty("FileChooser.useShellFolder", false)
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            result = chooser.selectedFile?.absolutePath
        }
    }
    result
}
