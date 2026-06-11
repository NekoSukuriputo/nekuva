package org.nekosukuriputo.nekuva.backups.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.swing.JFileChooser
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

    // Native L&F is set at app startup (Main.kt), so this resolves redirected folders too.
    private suspend fun chooseFile(save: Boolean, defaultName: String?): File? = withContext(Dispatchers.IO) {
        var result: File? = null
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser().apply {
                if (defaultName != null) selectedFile = File(defaultName)
            }
            val option = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
            if (option == JFileChooser.APPROVE_OPTION) {
                result = chooser.selectedFile
            }
        }
        result
    }
}
