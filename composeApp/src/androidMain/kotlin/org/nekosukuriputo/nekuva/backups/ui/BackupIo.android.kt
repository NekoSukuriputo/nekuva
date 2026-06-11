package org.nekosukuriputo.nekuva.backups.ui

import android.net.Uri
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
import java.io.OutputStream

@Composable
actual fun rememberBackupIo(): BackupIo {
    val context = LocalContext.current
    // Shared (remembered) deferreds so a recomposition during the SAF dialog doesn't lose the result.
    var pendingWrite by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var pendingRead by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> pendingWrite?.complete(uri) }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRead?.complete(uri) }

    return object : BackupIo {
        override suspend fun pickAndWrite(defaultName: String, writer: suspend (OutputStream) -> Unit): Boolean {
            val deferred = CompletableDeferred<Uri?>()
            pendingWrite = deferred
            createLauncher.launch(defaultName)
            val uri = deferred.await() ?: return false
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { writer(it) }
            }
            return true
        }

        override suspend fun pickAndRead(reader: suspend (InputStream) -> Unit): Boolean {
            val deferred = CompletableDeferred<Uri?>()
            pendingRead = deferred
            openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            val uri = deferred.await() ?: return false
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { reader(it) }
            }
            return true
        }
    }
}
