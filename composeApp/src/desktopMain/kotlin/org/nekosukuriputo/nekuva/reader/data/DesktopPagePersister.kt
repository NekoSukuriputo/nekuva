package org.nekosukuriputo.nekuva.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.reader.domain.PagePersister
import java.awt.Desktop
import java.io.File

/**
 * Desktop [PagePersister]: writes pages to `~/Pictures/Nekuva`. Desktop has no share sheet, so
 * "share" saves the file and reveals the folder in the system file manager.
 */
class DesktopPagePersister : PagePersister {

	private val targetDir: File
		get() = File(System.getProperty("user.home"), "Pictures/Nekuva").apply { mkdirs() }

	override suspend fun savePage(bytes: ByteArray, fileName: String, mimeType: String): String? =
		withContext(Dispatchers.IO) {
			runCatching {
				val file = File(targetDir, fileName)
				file.writeBytes(bytes)
				file.absolutePath
			}.getOrNull()
		}

	override suspend fun sharePage(bytes: ByteArray, fileName: String, mimeType: String): String? =
		withContext(Dispatchers.IO) {
			val path = savePage(bytes, fileName, mimeType)
			runCatching {
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
					Desktop.getDesktop().open(targetDir)
				}
			}
			path
		}
}
