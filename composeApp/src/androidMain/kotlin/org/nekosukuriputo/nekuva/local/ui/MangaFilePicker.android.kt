package org.nekosukuriputo.nekuva.local.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

@Composable
actual fun rememberMangaFilePicker(): MangaFilePicker {
    val context = LocalContext.current
    // Remembered deferreds so a recomposition during the SAF dialog doesn't lose the result.
    var pendingFile by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var pendingTree by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingFile?.complete(uri) }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> pendingTree?.complete(uri) }

    return object : MangaFilePicker {
        override suspend fun pickCbz(onPicked: suspend (name: String, input: InputStream) -> Unit): Boolean {
            val deferred = CompletableDeferred<Uri?>()
            pendingFile = deferred
            fileLauncher.launch(arrayOf("application/zip", "application/x-cbz", "application/octet-stream", "*/*"))
            val uri = deferred.await() ?: return false
            val name = resolveName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import.cbz"
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { onPicked(name, it) }
                    ?: error("Cannot open $uri")
            }
            return true
        }

        override suspend fun pickDirectory(
            onPicked: suspend (name: String, copyInto: suspend (destDir: File) -> Unit) -> Unit,
        ): Boolean {
            val deferred = CompletableDeferred<Uri?>()
            pendingTree = deferred
            treeLauncher.launch(null)
            val treeUri = deferred.await() ?: return false
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val name = displayName(context, DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId))
                ?: rootDocId.substringAfterLast('/').substringAfterLast(':').ifEmpty { "import" }
            onPicked(name) { dest ->
                withContext(Dispatchers.IO) { copyTree(context, treeUri, rootDocId, dest) }
            }
            return true
        }
    }
}

private fun resolveName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}

private fun displayName(context: Context, docUri: Uri): String? {
    return context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}

/** Recursively copy a SAF document tree into [destDir] (Doki importDirectory, DocumentsContract-based). */
private fun copyTree(context: Context, treeUri: Uri, parentDocId: String, destDir: File) {
    destDir.mkdirs()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val docId = c.getString(0)
            val dispName = c.getString(1) ?: continue
            val mime = c.getString(2)
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                copyTree(context, treeUri, docId, File(destDir, dispName))
            } else {
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    File(destDir, dispName).outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
