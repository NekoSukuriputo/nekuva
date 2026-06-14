package org.nekosukuriputo.nekuva.reader.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.reader.domain.PagePersister

/** Android [PagePersister]: writes to the MediaStore (Pictures/Nekuva) and shares via a content Uri. */
class AndroidPagePersister(
	private val context: Context,
) : PagePersister {

	override suspend fun savePage(bytes: ByteArray, fileName: String, mimeType: String): String? =
		withContext(Dispatchers.IO) {
			insert(bytes, fileName, mimeType)?.let { "${Environment.DIRECTORY_PICTURES}/$SUB_DIR/$fileName" }
		}

	override suspend fun sharePage(bytes: ByteArray, fileName: String, mimeType: String): String? {
		val uri = withContext(Dispatchers.IO) { insert(bytes, fileName, mimeType) } ?: return null
		val send = Intent(Intent.ACTION_SEND).apply {
			type = mimeType
			putExtra(Intent.EXTRA_STREAM, uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		runCatching {
			context.startActivity(
				Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
			)
		}
		return "${Environment.DIRECTORY_PICTURES}/$SUB_DIR/$fileName"
	}

	private fun insert(bytes: ByteArray, fileName: String, mimeType: String): Uri? = runCatching {
		val resolver = context.contentResolver
		val values = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
			put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SUB_DIR")
			}
		}
		val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
		} else {
			MediaStore.Images.Media.EXTERNAL_CONTENT_URI
		}
		val uri = resolver.insert(collection, values) ?: return null
		resolver.openOutputStream(uri)?.use { it.write(bytes) }
		uri
	}.getOrNull()

	private companion object {
		const val SUB_DIR = "Nekuva"
	}
}
