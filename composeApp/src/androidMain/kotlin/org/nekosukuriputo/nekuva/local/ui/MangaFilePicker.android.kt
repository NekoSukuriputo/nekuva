package org.nekosukuriputo.nekuva.local.ui

import android.net.Uri
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
import java.io.InputStream

@Composable
actual fun rememberMangaFilePicker(): MangaFilePicker {
    val context = LocalContext.current
    // Remembered deferred so a recomposition during the SAF dialog doesn't lose the result.
    var pending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pending?.complete(uri) }

    return object : MangaFilePicker {
        override suspend fun pickCbz(onPicked: suspend (name: String, input: InputStream) -> Unit): Boolean {
            val deferred = CompletableDeferred<Uri?>()
            pending = deferred
            launcher.launch(arrayOf("application/zip", "application/x-cbz", "application/octet-stream", "*/*"))
            val uri = deferred.await() ?: return false
            val name = resolveName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import.cbz"
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { onPicked(name, it) }
                    ?: error("Cannot open $uri")
            }
            return true
        }
    }
}

private fun resolveName(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}
