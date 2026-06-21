package org.nekosukuriputo.nekuva.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nekuva.composeapp.generated.resources.Res
import nekuva.composeapp.generated.resources.downloads
import org.jetbrains.compose.resources.getString
import org.koin.core.context.GlobalContext
import org.nekosukuriputo.nekuva.MainActivity
import org.nekosukuriputo.nekuva.download.domain.DownloadManager
import org.nekosukuriputo.nekuva.download.domain.DownloadState
import org.nekosukuriputo.nekuva.download.domain.DownloadStatus

/**
 * Foreground service backing the in-process [DownloadManager] (Doki's WorkManager foreground
 * notification). Observes the download list, posts an ongoing progress notification while any download
 * is RUNNING/QUEUED — keeping the process alive — and stops itself once the queue drains.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var channelName: String = "Downloads"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Resolve the channel name once (localized); a one-time in-memory resource read.
        channelName = runCatching { runBlocking { getString(Res.string.downloads) } }.getOrDefault("Downloads")
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after startForegroundService().
        startForegroundCompat(buildNotification(null, 0))
        if (!observing) {
            observing = true
            observe()
        }
        return START_NOT_STICKY
    }

    private fun observe() {
        val dm = GlobalContext.get().get<DownloadManager>()
        scope.launch {
            dm.downloads.collect { list ->
                val active = list.filter {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
                }
                if (active.isEmpty()) {
                    stopForegroundCompat()
                    stopSelf()
                    return@collect
                }
                val current = active.firstOrNull { it.status == DownloadStatus.RUNNING } ?: active.first()
                startForegroundCompat(buildNotification(current, active.size))
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: DownloadState?, activeCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val percent = state?.let { (it.percent * 100).toInt() } ?: 0
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(state?.manga?.title ?: channelName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
        if (state != null) {
            builder.setContentText(if (activeCount > 1) "$percent%  (+${activeCount - 1})" else "$percent%")
        }
        if (state != null && state.max > 0) {
            builder.setProgress(state.max, state.progress, state.isIndeterminate)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 0x444C // non-zero (required for a foreground notification)
    }
}
