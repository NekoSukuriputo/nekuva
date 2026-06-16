package org.nekosukuriputo.nekuva.reader.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import org.nekosukuriputo.nekuva.core.prefs.AppSettings
import org.nekosukuriputo.nekuva.reader.domain.PagePersister
import kotlin.coroutines.resume

/**
 * Android [PagePersister] (Doki parity): "ask every time" → SAF Create-Document prompt; else a configured
 * page-save dir (`pages_dir`, a SAF tree URI) → write into that tree via DocumentsContract; else fall back
 * to the MediaStore (Pictures/Nekuva). Share always goes through the MediaStore + a share intent.
 */
class AndroidPagePersister(
	private val context: Context,
	private val settings: AppSettings,
) : PagePersister {

	override suspend fun savePage(bytes: ByteArray, fileName: String, mimeType: String): String? {
		val ask = settings.isPagesSavingAskEnabled
		val dirUri = settings.getPagesSaveDirUri()
		// 1) Ask-every-time → let the user pick the file via SAF.
		if (ask) {
			val dest = createDocumentViaSaf(fileName, mimeType)
			if (dest != null) {
				writeTo(dest, bytes)
				return pageSaveDirLabel(dest.toString())
			}
		}
		// 2) Configured tree → write into it.
		if (dirUri != null) {
			val dest = withContext(Dispatchers.IO) { createInTree(dirUri, fileName, mimeType) }
			if (dest != null) {
				writeTo(dest, bytes)
				return "${pageSaveDirLabel(dirUri)}/$fileName"
			}
		}
		// 3) Fallback → MediaStore (Pictures/Nekuva).
		return withContext(Dispatchers.IO) {
			insert(bytes, fileName, mimeType)?.let { "${Environment.DIRECTORY_PICTURES}/$SUB_DIR/$fileName" }
		}
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

	private suspend fun writeTo(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
		runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
	}

	/** Create a file inside a persisted SAF tree URI (DocumentsContract; no androidx.documentfile dep). */
	private fun createInTree(treeUriString: String, fileName: String, mimeType: String): Uri? = runCatching {
		val treeUri = Uri.parse(treeUriString)
		val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
			treeUri,
			DocumentsContract.getTreeDocumentId(treeUri),
		)
		DocumentsContract.createDocument(context.contentResolver, parentDocUri, mimeType, fileName)
	}.getOrNull()

	/** SAF ACTION_CREATE_DOCUMENT prompt; needs the foreground Activity (null if unavailable). */
	private suspend fun createDocumentViaSaf(fileName: String, mimeType: String): Uri? {
		val activity = LocaleActivityHolder.current?.get() as? ComponentActivity ?: return null
		return withContext(Dispatchers.Main) {
			suspendCancellableCoroutine { cont ->
				val key = "save_page_${System.nanoTime()}"
				var launcher: ActivityResultLauncher<String>? = null
				launcher = activity.activityResultRegistry.register(
					key,
					ActivityResultContracts.CreateDocument(mimeType),
				) { result ->
					launcher?.unregister()
					if (cont.isActive) cont.resume(result)
				}
				cont.invokeOnCancellation { runCatching { launcher?.unregister() } }
				runCatching { launcher.launch(fileName) }.onFailure { if (cont.isActive) cont.resume(null) }
			}
		}
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
