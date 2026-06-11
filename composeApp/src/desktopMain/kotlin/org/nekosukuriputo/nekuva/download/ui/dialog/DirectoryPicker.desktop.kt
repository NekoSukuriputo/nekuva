package org.nekosukuriputo.nekuva.download.ui.dialog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual val supportsDirectoryPicker: Boolean = true

// The native Windows look & feel is set once at app startup (Main.kt), so the chooser here resolves
// redirected known folders (OneDrive "Documents") and supports creating new folders.
actual suspend fun pickMangaDirectory(): String? = withContext(Dispatchers.IO) {
    var result: String? = null
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            result = chooser.selectedFile?.absolutePath
        }
    }
    result
}
