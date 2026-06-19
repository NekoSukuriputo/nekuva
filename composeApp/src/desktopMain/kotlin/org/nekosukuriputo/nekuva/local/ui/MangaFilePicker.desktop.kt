package org.nekosukuriputo.nekuva.local.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberMangaFilePicker(): MangaFilePicker = DesktopMangaFilePicker

private object DesktopMangaFilePicker : MangaFilePicker {

    override suspend fun pickCbz(onPicked: suspend (name: String, input: InputStream) -> Unit): Boolean {
        val file = chooseFile() ?: return false
        withContext(Dispatchers.IO) {
            file.inputStream().use { onPicked(file.name, it) }
        }
        return true
    }

    private suspend fun chooseFile(): File? = withContext(Dispatchers.IO) {
        var result: File? = null
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser().apply {
                fileFilter = FileNameExtensionFilter("Comic book archive (*.cbz, *.zip)", "cbz", "zip")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                result = chooser.selectedFile
            }
        }
        result
    }
}
