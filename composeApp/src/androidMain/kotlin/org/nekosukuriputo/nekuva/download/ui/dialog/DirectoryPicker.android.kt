package org.nekosukuriputo.nekuva.download.ui.dialog

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import org.nekosukuriputo.nekuva.core.i18n.LocaleActivityHolder
import kotlin.coroutines.resume

// Android folder selection via SAF (OpenDocumentTree); the picked tree URI is resolved to a real file
// path (Doki parity) so the File-based download engine can write there. Writing to a shared-storage path
// requires "All files access" (MANAGE_EXTERNAL_STORAGE, declared in the manifest).
actual val supportsDirectoryPicker: Boolean = true

actual suspend fun pickMangaDirectory(): String? {
    val activity = LocaleActivityHolder.current?.get() as? ComponentActivity ?: return null
    val uri = suspendCancellableCoroutine<Uri?> { cont ->
        val key = "pick_manga_dir_${System.nanoTime()}"
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
    } ?: return null
    return runCatching {
        activity.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val path = treeUriToPath(uri)
        // Writing to shared storage via java.io.File needs "All files access" (API 30+). Nudge the user
        // to grant it so the download engine can actually write to the picked folder.
        if (path != null && !path.contains(activity.packageName) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
        ) {
            runCatching {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                )
            }
        }
        path
    }.getOrNull()
}

/** Resolves a SAF tree URI to a filesystem path (primary + named volumes; the common case). */
private fun treeUriToPath(uri: Uri): String? {
    val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
    val split = docId.split(':', limit = 2)
    val type = split[0]
    val relPath = split.getOrElse(1) { "" }
    val base = if (type.equals("primary", ignoreCase = true)) {
        Environment.getExternalStorageDirectory().absolutePath
    } else {
        "/storage/$type"
    }
    return if (relPath.isEmpty()) base else "$base/$relPath"
}
