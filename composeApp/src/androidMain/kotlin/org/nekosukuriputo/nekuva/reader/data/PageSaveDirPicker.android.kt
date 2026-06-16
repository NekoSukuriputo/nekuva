package org.nekosukuriputo.nekuva.reader.data

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import kotlin.coroutines.resume

/** Android: SAF OpenDocumentTree → persist the tree URI (page-save writes into it via DocumentsContract). */
actual suspend fun pickPageSaveDir(): String? {
    val activity = LocaleActivityHolder.current?.get() as? ComponentActivity ?: return null
    val uri = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Uri?> { cont ->
            val key = "pick_page_dir_${System.nanoTime()}"
            var launcher: ActivityResultLauncher<Uri?>? = null
            launcher = activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.OpenDocumentTree(),
            ) { result ->
                launcher?.unregister()
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { runCatching { launcher?.unregister() } }
            runCatching { launcher.launch(null) }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    } ?: return null
    return runCatching {
        activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        uri.toString()
    }.getOrNull()
}
