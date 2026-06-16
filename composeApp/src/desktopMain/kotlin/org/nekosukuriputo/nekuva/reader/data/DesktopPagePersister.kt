package org.nekosukuriputo.nekuva.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.reader.domain.PagePersister
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop [PagePersister]: writes pages to the configured default dir (Doki `pages_dir`), falling back to
 * `~/Pictures/Nekuva`. When "ask every time" (`pages_dir_ask`) is on, a Save dialog is shown per save.
 * Desktop has no share sheet, so "share" saves the file and reveals the folder in the file manager.
 */
class DesktopPagePersister(
	private val settings: AppSettings,
) : PagePersister {

	private val defaultDir: File
		get() = settings.getPagesSaveDirUri()?.let { File(it) }?.takeIf { it.isDirectory || it.mkdirs() }
			?: File(System.getProperty("user.home"), "Pictures/Nekuva").apply { mkdirs() }

	override suspend fun savePage(bytes: ByteArray, fileName: String, mimeType: String): String? =
		withContext(Dispatchers.IO) {
			runCatching {
				val target = if (settings.isPagesSavingAskEnabled) askForFile(fileName) else File(defaultDir, fileName)
				target?.also { it.writeBytes(bytes) }?.absolutePath
			}.getOrNull()
		}

	override suspend fun sharePage(bytes: ByteArray, fileName: String, mimeType: String): String? =
		withContext(Dispatchers.IO) {
			val path = savePage(bytes, fileName, mimeType)
			runCatching {
				val dir = path?.let { File(it).parentFile } ?: defaultDir
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
					Desktop.getDesktop().open(dir)
				}
			}
			path
		}

	/** Blocking AWT Save dialog (Doki "ask for destination every time"); null if the user cancels. */
	private fun askForFile(fileName: String): File? {
		val dialog = FileDialog(null as Frame?, "Nekuva", FileDialog.SAVE).apply {
			directory = defaultDir.absolutePath
			file = fileName
			isVisible = true
		}
		val dir = dialog.directory ?: return null
		val name = dialog.file ?: return null
		return File(dir, name)
	}
}
