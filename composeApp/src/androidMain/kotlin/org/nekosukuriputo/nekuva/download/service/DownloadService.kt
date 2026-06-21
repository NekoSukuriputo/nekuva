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
import nekuva.composeapp.generated.resources.cancel_all
import nekuva.composeapp.generated.resources.downloads
import nekuva.composeapp.generated.resources.pause
import nekuva.composeapp.generated.resources.resume
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
    // Action labels resolved once (localized) for the notification buttons.
    private var pauseLabel = "Pause"
    private var resumeLabel = "Resume"
    private var cancelLabel = "Cancel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Resolve the channel name + action labels once (localized); a one-time in-memory resource read.
        runCatching {
            runBlocking {
                channelName = getString(Res.string.downloads)
                pauseLabel = getString(Res.string.pause)
                resumeLabel = getString(Res.string.resume)
                cancelLabel = getString(Res.string.cancel_all)
            }
        }
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle the notification action buttons (pause/resume/cancel all).
        val dm = GlobalContext.get().get<DownloadManager>()
        when (intent?.action) {
            ACTION_PAUSE_ALL -> dm.pauseAll()
            ACTION_RESUME_ALL -> dm.resumeAll()
            ACTION_CANCEL_ALL -> dm.cancelAll()
        }
        // Must call startForeground promptly after startForegroundService().
        startForegroundCompat(buildNotification(null, 0, hasRunning = false))
        if (!observing) {
            observing = true
            observe(dm)
        }
        return START_NOT_STICKY
    }

    private fun observe(dm: DownloadManager) {
        scope.launch {
            dm.downloads.collect { list ->
                // Keep the notification while any download is unfinished (incl. paused → Resume action).
                val active = list.filter { !it.isFinished }
                if (active.isEmpty()) {
                    stopForegroundCompat()
                    stopSelf()
                    return@collect
                }
                val hasRunning = active.any { it.status == DownloadStatus.RUNNING }
                val current = active.firstOrNull { it.status == DownloadStatus.RUNNING } ?: active.first()
                startForegroundCompat(buildNotification(current, active.size, hasRunning))
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: DownloadState?, activeCount: Int, hasRunning: Boolean): Notification {
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
            // Pause-all (while running) or Resume-all (while paused), plus Cancel-all (Doki download actions).
            if (hasRunning) {
                builder.addAction(android.R.drawable.ic_media_pause, pauseLabel, actionIntent(ACTION_PAUSE_ALL))
            } else {
                builder.addAction(android.R.drawable.ic_media_play, resumeLabel, actionIntent(ACTION_RESUME_ALL))
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, actionIntent(ACTION_CANCEL_ALL))
        }
        if (state != null && state.max > 0) {
            builder.setProgress(state.max, state.progress, state.isIndeterminate)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
        const val ACTION_PAUSE_ALL = "org.nekosukuriputo.nekuva.download.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "org.nekosukuriputo.nekuva.download.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "org.nekosukuriputo.nekuva.download.CANCEL_ALL"
    }
}
