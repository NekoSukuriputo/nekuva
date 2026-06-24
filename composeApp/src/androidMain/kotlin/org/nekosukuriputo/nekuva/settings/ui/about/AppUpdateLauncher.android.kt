package org.nekosukuriputo.nekuva.settings.ui.about

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.nekosukuriputo.nekuva.core.github.AppVersion

@Composable
actual fun rememberAppUpdateLauncher(): AppUpdateLauncher {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppUpdateLauncher(context) }
}

/**
 * Android update applier (Doki `AppUpdateViewModel`): enqueue the APK on the system `DownloadManager`
 * (notification shows progress + completion), then fire the package installer when it finishes.
 */
private class AndroidAppUpdateLauncher(private val context: Context) : AppUpdateLauncher {

    override val canInstallInApp: Boolean = true

    override fun startUpdate(version: AppVersion) {
        val apkUrl = version.apkUrl
        if (apkUrl.isEmpty()) {
            // No APK asset attached to the release → fall back to opening the release page.
            openInBrowser(version.url)
            return
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = apkUrl.toUri()
        val fileName = uri.lastPathSegment ?: "nekuva-${version.name}.apk"
        val request = DownloadManager.Request(uri)
            .setTitle("Nekuva v${version.name}")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType(APK_MIME)
        val downloadId = runCatching { dm.enqueue(request) }.getOrElse {
            openInBrowser(version.url)
            return
        }
        // Launch the installer when THIS download completes (Doki onDownloadComplete). The receiver
        // unregisters itself so it doesn't outlive the one-shot install.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                runCatching { ctx.unregisterReceiver(this) }
                val contentUri = runCatching { dm.getUriForDownloadedFile(downloadId) }.getOrNull() ?: return
                val mime = dm.getMimeTypeForDownloadedFile(downloadId) ?: APK_MIME
                @Suppress("DEPRECATION")
                val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(contentUri, mime)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                runCatching { ctx.startActivity(install) }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun openInBrowser(url: String) {
        if (url.isEmpty()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
