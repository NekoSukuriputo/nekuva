package org.nekosukuriputo.nekuva.backups.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.create_backup
import nekuva.composeapp.generated.resources.done
import nekuva.composeapp.generated.resources.error_occurred
import nekuva.composeapp.generated.resources.loading
import nekuva.composeapp.generated.resources.restore_backup
import org.jetbrains.compose.resources.getString
import org.koin.core.context.GlobalContext

private const val CHANNEL_ID = "backups"
private const val NOTIFICATION_ID = 0x42_4B // "BK"

private fun context(): Context = GlobalContext.get().get()

private fun ensureChannel(ctx: Context, name: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        NotificationManagerCompat.from(ctx).createNotificationChannel(channel)
    }
}

private fun canPost(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

actual suspend fun notifyBackupStart(isRestore: Boolean) {
    val ctx = context()
    val title = getString(if (isRestore) Res.string.restore_backup else Res.string.create_backup)
    ensureChannel(ctx, title)
    if (!canPost(ctx)) return
    val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title)
        .setContentText(getString(Res.string.loading))
        .setOngoing(true)
        .setProgress(0, 0, true)
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
}

actual suspend fun notifyBackupFinish(isRestore: Boolean, success: Boolean) {
    val ctx = context()
    val title = getString(if (isRestore) Res.string.restore_backup else Res.string.create_backup)
    if (!canPost(ctx)) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID) }
        return
    }
    val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setContentTitle(title)
        .setContentText(getString(if (success) Res.string.done else Res.string.error_occurred))
        .setOngoing(false)
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
}
