package org.nekosukuriputo.nekuva.core.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

actual fun extensionsDir(): File =
    File(File(System.getProperty("user.home"), ".nekuva"), "extensions").apply { mkdirs() }

actual val extensionPlatformKey: String = "desktop"

actual val supportsExtensionImport: Boolean = true

actual suspend fun pickExtensionJar(): String? = withContext(Dispatchers.IO) {
    var result: String? = null
    // Native OS dialog (java.awt.FileDialog) — same reason as the backup picker: the Swing-L&F
    // JFileChooser freezes while navigating folders on Windows.
    SwingUtilities.invokeAndWait {
        val dialog = FileDialog(null as Frame?, "Nekuva", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".jar", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (name != null && dir != null) result = File(dir, name).absolutePath
    }
    result
}
