package org.nekosukuriputo.nekuva.backups.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.swing.SwingUtilities

@Composable
actual fun rememberBackupIo(): BackupIo = DesktopBackupIo

private object DesktopBackupIo : BackupIo {

    override suspend fun pickAndWrite(defaultName: String, writer: suspend (OutputStream) -> Unit): Boolean {
        val file = chooseFile(save = true, defaultName = defaultName) ?: return false
        val target = if (file.name.contains('.')) file else File(file.path + ".zip")
        withContext(Dispatchers.IO) {
            target.outputStream().use { writer(it) }
        }
        return true
    }

    override suspend fun pickAndRead(reader: suspend (InputStream) -> Unit): Boolean {
        val file = chooseFile(save = false, defaultName = null) ?: return false
        withContext(Dispatchers.IO) {
            file.inputStream().use { reader(it) }
        }
        return true
    }

    // Use the NATIVE OS file dialog (java.awt.FileDialog), not Swing JFileChooser: the Windows-L&F
    // JFileChooser does its folder listing on the EDT and freezes while navigating folders. FileDialog is
    // the real OS dialog — the OS handles navigation, so the JVM/EDT never blocks. Shown on the EDT.
    private suspend fun chooseFile(save: Boolean, defaultName: String?): File? = withContext(Dispatchers.IO) {
        var result: File? = null
        SwingUtilities.invokeAndWait {
            val dialog = FileDialog(
                null as Frame?,
                "Nekuva",
                if (save) FileDialog.SAVE else FileDialog.LOAD,
            )
            if (defaultName != null) dialog.file = defaultName
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file
            if (name != null && dir != null) result = File(dir, name)
        }
        result
    }
}
